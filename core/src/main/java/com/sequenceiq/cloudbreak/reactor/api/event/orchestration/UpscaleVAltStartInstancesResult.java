package com.sequenceiq.cloudbreak.reactor.api.event.orchestration;

import java.util.List;

import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.reactor.api.event.StackEvent;

public class UpscaleVAltStartInstancesResult extends StackEvent {

    List<InstanceMetaData> startedInstances;

    public UpscaleVAltStartInstancesResult(Long stackId, List<InstanceMetaData> startedInstances) {
        super(stackId);
        this.startedInstances = startedInstances;
    }

    public List<InstanceMetaData> getStartedInstances() {
        return startedInstances;
    }
}