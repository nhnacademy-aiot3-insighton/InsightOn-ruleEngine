package com.nhnacademy.insightonruleengine.client.core;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "insighton-core", path = "/internal/v1/groups", url = "${service-url.core}")
public interface CoreGroupClient {

    @GetMapping("/{group-id}/members")
    GroupMemberResponse getGroupMemberByUserId(@PathVariable("group-id") Long groupId,
                                               @RequestParam("userId") Long userId);
}
