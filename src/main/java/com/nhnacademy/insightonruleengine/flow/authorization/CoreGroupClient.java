package com.nhnacademy.insightonruleengine.flow.authorization;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "insighton-core", path = "/internal/groups")
public interface CoreGroupClient {

    @GetMapping("/{group-id}/members/user/{user-id}")
    GroupMemberResponse getGroupMemberByUserId(@PathVariable("group-id") Long groupId,
                                               @PathVariable("user-id") Long userId);
}
