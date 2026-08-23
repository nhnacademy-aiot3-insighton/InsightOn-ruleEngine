package com.nhnacademy.insightonruleengine.runner.application.lifecycle;

import com.nhnacademy.insightonruleengine.flow.domain.event.FlowRuntimeChangeEvent;
import com.nhnacademy.insightonruleengine.flow.domain.event.FlowRuntimeChangeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowRuntimeEventListener {

    private final FlowRuntimeSynchronizer flowRuntimeSynchronizer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void synchronize(FlowRuntimeChangeEvent event) {
        try{
            if(event.changeType() == FlowRuntimeChangeType.ACTIVATE){
                flowRuntimeSynchronizer.activate(event);
                return;
            }
            flowRuntimeSynchronizer.remove(event);
        }catch (RuntimeException e){
            log.error(
                    "Flow Runtime Redis synchronization failed. changeType={}, groupId={}, locationId={}, flowId={}",
                    event.changeType(),
                    event.groupId(),
                    event.locationId(),
                    event.flowId(),
                    e
            );
        }
    }
}
