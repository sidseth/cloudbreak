package com.sequenceiq.cloudbreak.reactor.api.event.orchestration;

import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterVAltCommissionViaCMRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.AbstractClusterScaleResult;

public class UpscaleClusterVAltCommissionViaCMResult extends AbstractClusterScaleResult<UpscaleClusterVAltCommissionViaCMRequest> {

    public UpscaleClusterVAltCommissionViaCMResult(UpscaleClusterVAltCommissionViaCMRequest request) {
        super(request);
    }
}
