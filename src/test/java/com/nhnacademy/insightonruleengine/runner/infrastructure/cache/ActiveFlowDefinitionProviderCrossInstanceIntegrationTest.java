package com.nhnacademy.insightonruleengine.runner.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FlowDefinitionAssembler.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ActiveFlowDefinitionProviderCrossInstanceIntegrationTest {

    private static final Long GROUP_ID = 91_001L;
    private static final Long LOCATION_ID = 92_001L;
    private static final Duration LOCAL_FALLBACK_MAX_AGE = Duration.ofSeconds(30);

    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private FlowDefinitionAssembler flowDefinitionAssembler;

    @AfterEach
    void cleanUp() {
        flowRepository.deleteAll();
    }

    @Test
    void staleSnapshotDoesNotExecuteFlowDeactivatedByAnotherInstance() {
        SharedFlowDefinitionCache sharedCache = new SharedFlowDefinitionCache();
        AtomicLong nanoTime = new AtomicLong();
        ActiveFlowDefinitionProvider firstInstance = provider(sharedCache, nanoTime);
        ActiveFlowDefinitionProvider secondInstance = provider(sharedCache, nanoTime);
        Flow flow = flowRepository.saveAndFlush(
                new Flow(GROUP_ID, LOCATION_ID, "교차 인스턴스 캐시 검증", null, FlowStatus.ACTIVE)
        );

        assertEquals(List.of(flow.getId()), flowIds(firstInstance.find(GROUP_ID, LOCATION_ID)));

        sharedCache.makeUnavailable();
        flow.changeActivationStatus(FlowStatus.INACTIVE);
        flowRepository.saveAndFlush(flow);
        secondInstance.refreshAfterCommit(GROUP_ID, LOCATION_ID);
        nanoTime.addAndGet(LOCAL_FALLBACK_MAX_AGE.toNanos() + 1L);

        assertEquals(List.of(), firstInstance.find(GROUP_ID, LOCATION_ID));
    }

    private ActiveFlowDefinitionProvider provider(
            FlowDefinitionCache cache,
            AtomicLong nanoTime
    ) {
        return new ActiveFlowDefinitionProvider(
                flowRepository,
                flowDefinitionAssembler,
                cache,
                nanoTime::get,
                LOCAL_FALLBACK_MAX_AGE
        );
    }

    private List<Long> flowIds(List<FlowDefinition> definitions) {
        return definitions.stream().map(FlowDefinition::flowId).toList();
    }

    private static final class SharedFlowDefinitionCache implements FlowDefinitionCache {

        private final Map<String, List<FlowDefinition>> values = new ConcurrentHashMap<>();
        private final AtomicBoolean available = new AtomicBoolean(true);

        @Override
        public Optional<List<FlowDefinition>> find(Long groupId, Long locationId) {
            requireAvailable();
            return Optional.ofNullable(values.get(key(groupId, locationId)));
        }

        @Override
        public void replace(Long groupId, Long locationId, List<FlowDefinition> definitions) {
            requireAvailable();
            values.put(key(groupId, locationId), List.copyOf(definitions));
        }

        @Override
        public void evict(Long groupId, Long locationId) {
            requireAvailable();
            values.remove(key(groupId, locationId));
        }

        private void makeUnavailable() {
            available.set(false);
        }

        private void requireAvailable() {
            if (!available.get()) {
                throw new RedisConnectionFailureException("Redis unavailable");
            }
        }

        private String key(Long groupId, Long locationId) {
            return groupId + ":" + locationId;
        }
    }
}
