package com.nhnacademy.insightonruleengine.flow.cleanup;

import com.nhnacademy.insightonruleengine.flow.repository.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.repository.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupDeletionDatabaseCleanupService {

    private final LinkRepository linkRepository;
    private final NodeRepository nodeRepository;
    private final FlowRepository flowRepository;

    @Transactional
    public void deleteByGroupId(Long groupId) {
        linkRepository.deleteByGroupId(groupId);
        nodeRepository.deleteByGroupId(groupId);
        flowRepository.deleteByGroupId(groupId);
    }

    @Transactional
    public void deleteByLocationId(Long locationId) {
        linkRepository.deleteByLocationId(locationId);
        nodeRepository.deleteByLocationId(locationId);
        flowRepository.deleteByLocationId(locationId);
    }
}
