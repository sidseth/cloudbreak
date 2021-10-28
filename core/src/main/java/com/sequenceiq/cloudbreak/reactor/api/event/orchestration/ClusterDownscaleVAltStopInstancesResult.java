package com.sequenceiq.cloudbreak.reactor.api.event.orchestration;

import java.util.List;

import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;
import com.sequenceiq.cloudbreak.cloud.model.CloudVmInstanceStatus;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.reactor.api.event.StackEvent;

public class ClusterDownscaleVAltStopInstancesResult extends StackEvent {

    private final List<CloudInstance> cloudInstancesToStop;

    private final List<CloudVmInstanceStatus> cloudVmInstanceStatusesNoCheck;

    private final List<InstanceMetaData> toStopInstanceMetadata;

    public ClusterDownscaleVAltStopInstancesResult(Long stackId, List<CloudInstance> cloudInstancesToStop, List<CloudVmInstanceStatus> cloudVmInstanceStatusesNoCheck, List<InstanceMetaData> toStopInstanceMetadata) {
        super(stackId);
        this.cloudInstancesToStop = cloudInstancesToStop;
        this.cloudVmInstanceStatusesNoCheck = cloudVmInstanceStatusesNoCheck;
        this.toStopInstanceMetadata = toStopInstanceMetadata;
    }

    public List<CloudInstance> getCloudInstancesToStop() {
        return cloudInstancesToStop;
    }

    public List<CloudVmInstanceStatus> getCloudVmInstanceStatusesNoCheck() {
        return cloudVmInstanceStatusesNoCheck;
    }

    // TODO ZZZ: This is all wrong. Should be the actual instances which were stopped. Also instances which were not STOPPED etc. This will eventually evolve based on how we want to handle errors.
    public List<InstanceMetaData> getToStopInstanceMetadata() {
        return toStopInstanceMetadata;
    }
}
