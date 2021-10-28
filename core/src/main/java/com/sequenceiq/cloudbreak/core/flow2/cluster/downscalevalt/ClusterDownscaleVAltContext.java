package com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt;

import java.util.Set;

import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;
import com.sequenceiq.cloudbreak.common.type.ClusterManagerType;
import com.sequenceiq.cloudbreak.core.flow2.stack.StackContext;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.view.StackView;
import com.sequenceiq.flow.core.FlowParameters;

public class ClusterDownscaleVAltContext extends StackContext {

    private final String hostGroupName;

    // One of the below 2 should be present. i.e. stop a random set of nodes, OR stop a specific set of nodes.
    private final Set<Long> hostIdsToRemove;

    private final Integer adjustment;

    private final Boolean singlePrimaryGateway;

    // TODO ZZZ: Will likely be required when implementing the CM side to figure out the master? Remove later.
//    private final String primaryGatewayHostName;

    private final ClusterManagerType clusterManagerType;

    private final Boolean restartServices;

    private final StackView stackView;

    public ClusterDownscaleVAltContext(FlowParameters flowParameters, Stack stack, StackView stackView,
            CloudContext cloudContext, CloudCredential cloudCredential, CloudStack cloudStack,
            String hostGroupName, Set<Long> hostIdsToRemove, Integer adjustment,
            Boolean singlePrimaryGateway,
            ClusterManagerType clusterManagerType, Boolean restartServices) {
        super(flowParameters, stack, cloudContext, cloudCredential, cloudStack);
        this.hostGroupName = hostGroupName;
        this.hostIdsToRemove = hostIdsToRemove;
        this.adjustment = adjustment;
        this.singlePrimaryGateway = singlePrimaryGateway;
        this.clusterManagerType = clusterManagerType;
        this.restartServices = restartServices;
        this.stackView = stackView;
    }

    public String getHostGroupName() {
        return hostGroupName;
    }

    public Set<Long> getHostIdsToRemove() {
        return hostIdsToRemove;
    }

    public Integer getAdjustment() {
        return adjustment;
    }

    public Boolean getSinglePrimaryGateway() {
        return singlePrimaryGateway;
    }

    public ClusterManagerType getClusterManagerType() {
        return clusterManagerType;
    }

    public Boolean getRestartServices() {
        return restartServices;
    }

    public StackView getStackView() {
        return stackView;
    }

    @Override
    public String toString() {
        return "ClusterDownscaleVAltContext{" +
                "hostGroupName='" + hostGroupName + '\'' +
                ", hostIdsToRemove=" + hostIdsToRemove +
                ", adjustment=" + adjustment +
                ", singlePrimaryGateway=" + singlePrimaryGateway +
                ", clusterManagerType=" + clusterManagerType +
                ", restartServices=" + restartServices +
                ", stackView=" + stackView +
                '}';
    }
}
