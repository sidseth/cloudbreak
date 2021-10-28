package com.sequenceiq.cloudbreak.reactor.api.event.cluster;

import java.util.List;
import java.util.Set;

import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.event.resource.CloudStackRequest;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;

public class ClusterDownscaleVAltStopInstancesRequest<T> extends CloudStackRequest<T> {

    private final Stack stack;

    private final String hsotGroupName;

    private final List<InstanceMetaData> instanceMetaDataForEntireHg;

    // TODO ZZZ: See if this is a list of strings or CloudInstances of some kind.
    private final Set<Long> instanceIdsToStop;

    public ClusterDownscaleVAltStopInstancesRequest(CloudContext cloudContext, CloudCredential cloudCredential, CloudStack cloudStack, Stack stack, String hsotGroupName, List<InstanceMetaData> instanceMetaDataForEntireHg, Set<Long> instanceIdsToStop) {
        super(cloudContext, cloudCredential, cloudStack);
        this.stack = stack;
        this.hsotGroupName = hsotGroupName;
        this.instanceMetaDataForEntireHg = instanceMetaDataForEntireHg;
        this.instanceIdsToStop = instanceIdsToStop;
    }

    public Stack getStack() {
        return stack;
    }

    public String getHsotGroupName() {
        return hsotGroupName;
    }

    // TODO ZZZ: Get rid of this. Instead of sending getInstanceIdsToStop ... send the actual metadata only for the instances that need to be stopped
    public List<InstanceMetaData> getInstanceMetaDataForEntireHg() {
        return instanceMetaDataForEntireHg;
    }

    public Set<Long> getInstanceIdsToStop() {
        return instanceIdsToStop;
    }

    @Override
    public String toString() {
        return "DownscaleClusterVAltStopInstancesRequest{" +
                "stack=" + stack +
                ", hsotGroupName='" + hsotGroupName + '\'' +
                ", instanceMetaDataForEntireHg=" + instanceMetaDataForEntireHg +
                ", instanceIdsToStop=" + instanceIdsToStop +
                '}';
    }
}
