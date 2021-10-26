package com.sequenceiq.cloudbreak.reactor.api.event.orchestration;

import com.sequenceiq.cloudbreak.reactor.api.event.StackEvent;

public class UpscaleClusterVAltCommissionViaCMResult extends StackEvent {
    public UpscaleClusterVAltCommissionViaCMResult(Long stackId) {
        super(stackId);
    }

    public UpscaleClusterVAltCommissionViaCMResult(String selector, Long stackId) {
        super(selector, stackId);
    }
}
