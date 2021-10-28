package com.sequenceiq.cloudbreak.reactor.api.event.orchestration;

import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterDownscaleVAltDecommissionViaCMRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.AbstractClusterScaleResult;

public class ClusterDownscaleVAltDecommissionViaCMResult extends AbstractClusterScaleResult<ClusterDownscaleVAltDecommissionViaCMRequest> {

    public ClusterDownscaleVAltDecommissionViaCMResult(ClusterDownscaleVAltDecommissionViaCMRequest request) {
        super(request);
    }
}