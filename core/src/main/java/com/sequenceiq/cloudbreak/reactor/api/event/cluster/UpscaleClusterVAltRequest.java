package com.sequenceiq.cloudbreak.reactor.api.event.cluster;

import java.util.List;

import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.event.resource.CloudStackRequest;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;

public class UpscaleClusterVAltRequest<T> extends CloudStackRequest<T> {

    private final Stack stack;

    private final String hostGroupName;

    private final int numInstancesToStart;

    private final List<InstanceMetaData> instanceMetaDataForHg;

    public UpscaleClusterVAltRequest(CloudContext cloudContext, CloudCredential cloudCredential, CloudStack cloudStack, Stack stack, String hostGroupName, int numInstancesToStart, List<InstanceMetaData> instanceMetadataForHostGroup) {
        super(cloudContext, cloudCredential, cloudStack);
        this.stack = stack;
        this.hostGroupName = hostGroupName;
        this.numInstancesToStart = numInstancesToStart;
        this.instanceMetaDataForHg = instanceMetadataForHostGroup;
    }

    public Stack getStack() {
        return stack;
    }

    public String getHostGroupName() {
        return hostGroupName;
    }

    public int getNumInstancesToStart() {
        return numInstancesToStart;
    }

    public List<InstanceMetaData> getInstanceMetaDataForHg() {
        return instanceMetaDataForHg;
    }

    @Override
    public String toString() {
        return "UpscaleClusterVAltRequest{" +
                "hostGroupName='" + hostGroupName + '\'' +
                ", numInstancesToStart=" + numInstancesToStart +
                ", instanceMetaDataForHg=" + instanceMetaDataForHg +
                '}';
    }
}
