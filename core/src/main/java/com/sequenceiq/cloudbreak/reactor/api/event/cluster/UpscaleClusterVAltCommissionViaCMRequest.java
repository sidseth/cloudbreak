package com.sequenceiq.cloudbreak.reactor.api.event.cluster;

import com.sequenceiq.cloudbreak.reactor.api.event.resource.AbstractClusterScaleRequest;

public class UpscaleClusterVAltCommissionViaCMRequest extends AbstractClusterScaleRequest {

    // ZZZ: TODO: Introduce the set of hosts which were successfully started - to be sent to the next step.

    public UpscaleClusterVAltCommissionViaCMRequest(Long stackId, String hostGroupName) {
        super(stackId, hostGroupName);
    }
}
