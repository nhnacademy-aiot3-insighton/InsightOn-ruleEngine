package com.nhnacademy.insightonruleengine.flow.application.authorization;

import com.nhnacademy.insightonruleengine.flow.domain.exception.CoreDependencyException;
import com.nhnacademy.insightonruleengine.flow.domain.exception.ForbiddenException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GroupAuthorizationService {

    private final CoreGroupClient coreGroupClient;

    public GroupRole requireMembership(Long groupId, Long userId) {
        try {
            GroupMemberResponse member = coreGroupClient.getGroupMemberByUserId(groupId, userId);

            if (member == null) {
                throw new CoreDependencyException("Core 그룹 멤버 응답을 처리할 수 없습니다.");
            }
            if (!groupId.equals(member.groupId())) {
                throw new ForbiddenException(
                        "Core 응답의 groupId가 요청과 다릅니다. requested:" + groupId + ", returned:" + member.groupId());
            }
            if (member.groupRole() == null) {
                throw new ForbiddenException("Core 응답에서 그룹 역할을 확인할 수 없습니다.");
            }

            return member.groupRole();
        } catch (FeignException.NotFound e) {
            throw new ForbiddenException("groupId:" + groupId + "의 멤버가 아닙니다. userId:" + userId);
        } catch (FeignException e) {
            throw new CoreDependencyException("Core 그룹 권한 조회에 실패했습니다.", e);
        }
    }

    public void requireRole(Long groupId, Long userId, GroupRole minimumRole) {
        GroupRole role = requireMembership(groupId, userId);
        if (!role.isAtLeast(minimumRole)) {
            throw new ForbiddenException(
                    "groupId:" + groupId + "에서 " + minimumRole + " 이상 권한이 필요합니다. userId:" + userId);
        }
    }
}
