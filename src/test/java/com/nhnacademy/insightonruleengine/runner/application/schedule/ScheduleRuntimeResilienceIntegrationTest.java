package com.nhnacademy.insightonruleengine.runner.application.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.config.ScheduleExecutionProperties;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.ScheduleParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.runner.application.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.RedisKeyFactory;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.ScheduleExecutionRedisRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ScheduleRuntimeResilienceIntegrationTest {

    private static final Duration REDIS_COMMAND_TIMEOUT = Duration.ofMillis(500);
    private static final ScheduleExecutionProperties SCHEDULE_PROPERTIES =
            new ScheduleExecutionProperties("Asia/Seoul", Duration.ofMinutes(10), 2);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisKeyFactory redisKeyFactory;

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(REDIS_COMMAND_TIMEOUT)
                .shutdownTimeout(Duration.ZERO)
                .build();
        connectionFactory = new LettuceConnectionFactory(redisConfiguration, clientConfiguration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisKeyFactory = new RedisKeyFactory();
    }

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @AfterAll
    static void closeRedis() {
        connectionFactory.destroy();
    }

    @Test
    void twoSchedulersExecuteSameOccurrenceOnlyOnce() {
        AtomicInteger executionCount = new AtomicInteger();
        FlowDefinition definition = scheduleFlow();
        SchedulerInstance first = schedulerInstance(definition, executionCount);
        SchedulerInstance second = schedulerInstance(definition, executionCount);
        Instant scheduledAt = prepareSameOccurrence(
                first,
                second,
                Instant.parse("2026-08-30T00:00:30Z")
        );

        runConcurrently(first.task(), second.task());

        assertEquals(1, executionCount.get());
        assertEquals(
                "1",
                redisTemplate.opsForValue().get(redisKeyFactory.scheduleExecution(10L, scheduledAt))
        );
    }

    @Test
    void redisOutageSkipsOccurrenceAndNextOccurrenceExecutesOnceAfterRecovery() {
        AtomicInteger executionCount = new AtomicInteger();
        FlowDefinition definition = scheduleFlow();
        SchedulerInstance first = schedulerInstance(definition, executionCount);
        SchedulerInstance second = schedulerInstance(definition, executionCount);
        prepareSameOccurrence(first, second, Instant.parse("2026-08-30T00:00:30Z"));

        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            runConcurrently(first.task(), second.task());
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }

        assertEquals(0, executionCount.get());
        await().atMost(Duration.ofSeconds(5))
                .ignoreExceptions()
                .until(() -> {
                    redisTemplate.opsForValue().set("schedule-recovery-probe", "ready");
                    return "ready".equals(
                            redisTemplate.opsForValue().get("schedule-recovery-probe"));
                });

        prepareSameOccurrence(first, second, Instant.parse("2026-08-30T00:01:30Z"));
        runConcurrently(first.task(), second.task());

        assertEquals(1, executionCount.get());
    }

    private SchedulerInstance schedulerInstance(
            FlowDefinition definition,
            AtomicInteger executionCount
    ) {
        FlowRepository flowRepository = mock(FlowRepository.class);
        FlowDefinitionAssembler assembler = mock(FlowDefinitionAssembler.class);
        NodeParamsParser nodeParamsParser = mock(NodeParamsParser.class);
        FlowRunner flowRunner = mock(FlowRunner.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        AtomicReference<Runnable> task = new AtomicReference<>();
        AtomicReference<Trigger> trigger = new AtomicReference<>();

        when(assembler.assembleActive(1L, 10L)).thenReturn(definition);
        when(nodeParamsParser.<ScheduleParams>parse(eq(NodeType.SCHEDULE), any()))
                .thenReturn(new ScheduleParams("0 * * * * *"));
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenAnswer(invocation -> {
                    task.set(invocation.getArgument(0));
                    trigger.set(invocation.getArgument(1));
                    return mock(ScheduledFuture.class);
                });
        doAnswer(invocation -> {
            executionCount.incrementAndGet();
            return null;
        }).when(flowRunner).runScheduled(eq(definition), any(Instant.class));

        ScheduleExecutionRedisRepository executionRepository =
                new ScheduleExecutionRedisRepository(
                        redisTemplate,
                        redisKeyFactory,
                        SCHEDULE_PROPERTIES
                );
        ScheduleFlowScheduler scheduler = new ScheduleFlowScheduler(
                flowRepository,
                assembler,
                nodeParamsParser,
                flowRunner,
                executionRepository,
                SCHEDULE_PROPERTIES,
                taskScheduler
        );
        scheduler.registerAfterCommit(1L, 10L);

        assertNotNull(task.get());
        assertNotNull(trigger.get());
        return new SchedulerInstance(task.get(), trigger.get());
    }

    private Instant prepareSameOccurrence(
            SchedulerInstance first,
            SchedulerInstance second,
            Instant clockInstant
    ) {
        Clock clock = Clock.fixed(clockInstant, ZoneOffset.UTC);
        Instant firstScheduledAt = first.trigger().nextExecution(new SimpleTriggerContext(clock));
        Instant secondScheduledAt = second.trigger().nextExecution(new SimpleTriggerContext(clock));

        assertNotNull(firstScheduledAt);
        assertEquals(firstScheduledAt, secondScheduledAt);
        return firstScheduledAt;
    }

    private void runConcurrently(Runnable first, Runnable second) {
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Void> firstExecution = CompletableFuture.runAsync(
                () -> runAfter(start, first)
        );
        CompletableFuture<Void> secondExecution = CompletableFuture.runAsync(
                () -> runAfter(start, second)
        );
        start.countDown();
        CompletableFuture.allOf(firstExecution, secondExecution).join();
    }

    private void runAfter(CountDownLatch start, Runnable task) {
        try {
            start.await();
            task.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 실행 테스트가 중단됐습니다.", exception);
        }
    }

    private FlowDefinition scheduleFlow() {
        return new FlowDefinition(
                10L,
                1L,
                20L,
                "다중 인스턴스 정기 실행",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-30T00:00:00Z"),
                List.of(new NodeDefinition(
                        100L,
                        NodeType.SCHEDULE,
                        JsonNodeFactory.instance.objectNode().put("cron", "0 * * * * *")
                )),
                List.of()
        );
    }

    private record SchedulerInstance(Runnable task, Trigger trigger) {
    }
}
