package com.sequenceiq.cloudbreak.reactor.api.event.orchestration;

import java.util.Set;

import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterDownscaleVAltDecommissionViaCMRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.AbstractClusterScaleResult;

public class ClusterDownscaleVAltDecommissionViaCMResult extends AbstractClusterScaleResult<ClusterDownscaleVAltDecommissionViaCMRequest> {

    private final Set<String> decommissionedHostFqdns;

    public ClusterDownscaleVAltDecommissionViaCMResult(ClusterDownscaleVAltDecommissionViaCMRequest request, Set<String> decommissionedHostFqdns) {
        super(request);
        this.decommissionedHostFqdns = decommissionedHostFqdns;
    }

    public Set<String> getDecommissionedHostFqdns() {
        return decommissionedHostFqdns;
    }

    @Override
    public String toString() {
        return "ClusterDownscaleVAltDecommissionViaCMResult{" +
                "decommissionedHostFqdns=" + decommissionedHostFqdns +
                '}';
    }
}