package com.nhnacademy.insightonruleengine.client.core;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "insighton-core",
        contextId = "coreActuatorClient",
        path = "/internal/v1/locations",
        url = "${service-url.core}"
)
public interface CoreActuatorClient {

    @PutMapping("/{location-id}/actuators/state")
    void updateActuatorState(
            @PathVariable("location-id") Long locationId,
            @RequestBody ActuatorCommandRequest request
    );

    // AI draft를 즉시 ACTIVE로 만들지 판단하기 위해 위치의 autoControlMode를 조회합니다.
    @GetMapping("/{location-id}")
    LocationResponse getLocation(@PathVariable("location-id") Long locationId);
}
