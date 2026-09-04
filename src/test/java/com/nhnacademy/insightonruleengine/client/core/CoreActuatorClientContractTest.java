package com.nhnacademy.insightonruleengine.client.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

class CoreActuatorClientContractTest {

    @Test
    void matchesCoreInternalApiPaths() throws NoSuchMethodException {
        FeignClient feignClient = CoreActuatorClient.class.getAnnotation(FeignClient.class);
        assertEquals("/internal/v1", feignClient.path());

        Method update = CoreActuatorClient.class.getMethod(
                "updateActuatorState",
                Long.class,
                Long.class,
                ActuatorCommandRequest.class
        );
        assertArrayEquals(
                new String[]{"/groups/{group-id}/locations/{location-id}/actuators/state"},
                update.getAnnotation(PutMapping.class).value()
        );

        Method getLocation = CoreActuatorClient.class.getMethod("getLocation", Long.class);
        assertArrayEquals(
                new String[]{"/locations/{location-id}"},
                getLocation.getAnnotation(GetMapping.class).value()
        );
    }
}
