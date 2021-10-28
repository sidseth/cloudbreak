package com.sequenceiq.cloudbreak.reactor.api.event.cluster;

import java.util.Set;

import com.sequenceiq.cloudbreak.reactor.api.event.resource.AbstractClusterScaleRequest;


public class ClusterDownscaleVAltDecommissionViaCMRequest extends AbstractClusterScaleRequest {

    // TODO ZZZ: Which ids are these exactly? getPrivateId I think. Trace back to what AutoScale sends,
    //  and how the incoming layers translate this information.
    private final Set<Long> instanceIdsToDecommission;

    public ClusterDownscaleVAltDecommissionViaCMRequest(Long stackId, String hostGroupName, Set<Long> instanceIdsToDecommission) {
        super(stackId, hostGroupName);
        this.instanceIdsToDecommission = instanceIdsToDecommission;
    }

    public Set<Long> getInstanceIdsToDecommission() {
        return instanceIdsToDecommission;
    }

    @Override
    public String toString() {
        return "DownscalClusterVAltDecommissionViaCMRequest{" +
                "instanceIdsToDecommission=" + instanceIdsToDecommission +
                '}';
    }
}
