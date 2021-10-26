package com.sequenceiq.cloudbreak.core.flow2.event;

import java.util.Set;

import com.sequenceiq.cloudbreak.common.type.ClusterManagerType;
import com.sequenceiq.cloudbreak.reactor.api.event.HostGroupPayload;
import com.sequenceiq.cloudbreak.reactor.api.event.StackEvent;

public class ClusterScaleVAltTriggerEvent extends StackEvent implements HostGroupPayload {

    private final String hostGroup;

    private final Integer adjustment;

    private final Set<String> hostNames;

    private final boolean singlePrimaryGateway;

    private final boolean restartServices;

    private final ClusterManagerType clusterManagerType;

    public ClusterScaleVAltTriggerEvent(String selector, Long stackId, String hostGroup, Integer adjustment, Set<String> hostNames, boolean singlePrimaryGateway,
            boolean restartServices, ClusterManagerType clusterManagerType) {
        super(selector, stackId);
        this.hostGroup = hostGroup;
        this.adjustment = adjustment;
        this.hostNames = hostNames;
        this.singlePrimaryGateway = singlePrimaryGateway;
        this.restartServices = restartServices;
        this.clusterManagerType = ClusterManagerType.CLOUDERA_MANAGER;
    }


    @Override
    public String getHostGroupName() {
        return null;
    }

    public Integer getAdjustment() {
        return adjustment;
    }

    public Set<String> getHostNames() {
        return hostNames;
    }

    public boolean isSinglePrimaryGateway() {
        return singlePrimaryGateway;
    }

    public boolean isRestartServices() {
        return restartServices;
    }

    public ClusterManagerType getClusterManagerType() {
        return clusterManagerType;
    }

    @Override
    public String toString() {
        return "ClusterScaleVAltTriggerEvent{" +
                "hostGroup='" + hostGroup + '\'' +
                ", adjustment=" + adjustment +
                ", hostNames=" + hostNames +
                ", singlePrimaryGateway=" + singlePrimaryGateway +
                ", restartServices=" + restartServices +
                ", clusterManagerType=" + clusterManagerType +
                '}';
    }
}
