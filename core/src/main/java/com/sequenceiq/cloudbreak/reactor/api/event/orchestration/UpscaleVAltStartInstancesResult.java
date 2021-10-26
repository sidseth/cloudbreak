package com.sequenceiq.cloudbreak.reactor.api.event.orchestration;

import com.sequenceiq.cloudbreak.reactor.api.event.StackEvent;

// TODO ZZZ: This needs to includes the hostnames that were started, so that the same can be sent to CM
public class UpscaleVAltStartInstancesResult extends StackEvent {
    public UpscaleVAltStartInstancesResult(Long stackId) {
        super(stackId);
    }

    public UpscaleVAltStartInstancesResult(String selector, Long stackId) {
        super(selector, stackId);
    }
}