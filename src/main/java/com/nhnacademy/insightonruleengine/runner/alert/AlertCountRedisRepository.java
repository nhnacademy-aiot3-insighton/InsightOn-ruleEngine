package com.nhnacademy.insightonruleengine.runner.alert;

import com.nhnacademy.insightonruleengine.runner.redis.RedisKeyFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

//ALERT Action Node에 도달한 횟수 카운트해서 Redis에 누적시킵니다.
@Repository
@RequiredArgsConstructor
public class AlertCountRedisRepository {

    private static final DefaultRedisScript<Long> INCREMENT_AND_RESET_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count < tonumber(ARGV[1]) then return 0 end; "
                    + "redis.call('DEL', KEYS[1]); "
                    + "return 1",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;

    //목표 카운트가 1이하면 만들어주는 의미가 없습니다.
    public boolean incrementAndCheck(Long flowId, Long alertActionNodeId, int requiredCount) {
        String key = redisKeyFactory.count(flowId, alertActionNodeId);
        Long result = redisTemplate.execute(INCREMENT_AND_RESET_SCRIPT, List.of(key), String.valueOf(requiredCount));

        return Long.valueOf(1L).equals(result);
    }
}
