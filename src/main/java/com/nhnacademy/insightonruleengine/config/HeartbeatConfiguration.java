package com.nhnacademy.insightonruleengine.config;

import com.nhnacademy.insightonruleengine.heartbeat.EngineHeartbeatRepository;
import com.nhnacademy.insightonruleengine.heartbeat.EngineHeartbeatService;
import com.nhnacademy.insightonruleengine.heartbeat.HeartbeatProperties;
import com.nhnacademy.insightonruleengine.heartbeat.HeartbeatScheduler;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.RedisKeyFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//heartbeat에 필요한 서비스와 스케쥴러를 등록해줍니다.
@Configuration
@ConditionalOnProperty(prefix = "rule-engine.heartbeat", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(HeartbeatProperties.class)
public class HeartbeatConfiguration {

    //heartbeat 설정값을 확인, 서로 다른 엔진을 관리할 서비스를 만듭니다.
    @Bean
    EngineHeartbeatService engineHeartbeatService(
            EngineHeartbeatRepository heartbeatRepository,
            HeartbeatProperties heartbeatProperties,
            RedisKeyFactory redisKeyFactory
    ) {
        heartbeatProperties.validateConfiguration();
        redisKeyFactory.heartbeat(heartbeatProperties.engineId());
        redisKeyFactory.heartbeat(heartbeatProperties.peerEngineId());
        return new EngineHeartbeatService(heartbeatRepository, heartbeatProperties);
    }

    // 정해진 시간마다 호출해줄 스케쥴러를 만들어줍니다.
    @Bean
    HeartbeatScheduler heartbeatScheduler(EngineHeartbeatService engineHeartbeatService) {
        return new HeartbeatScheduler(engineHeartbeatService);
    }
}
