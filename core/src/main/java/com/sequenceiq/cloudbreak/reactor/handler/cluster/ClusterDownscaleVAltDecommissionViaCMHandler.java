package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterDownscaleVAltDecommissionViaCMRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.ClusterDownscaleVAltDecommissionViaCMResult;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.flow.reactor.api.handler.EventHandler;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class ClusterDownscaleVAltDecommissionViaCMHandler implements EventHandler<ClusterDownscaleVAltDecommissionViaCMRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterDownscaleVAltDecommissionViaCMHandler.class);

    @Inject
    private EventBus eventBus;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(ClusterDownscaleVAltDecommissionViaCMRequest.class);
    }

    @Override
    public void accept(Event<ClusterDownscaleVAltDecommissionViaCMRequest> event) {
        ClusterDownscaleVAltDecommissionViaCMRequest request = event.getData();
        LOGGER.info("ZZZ: ClusterDownscaleVAltDecommissionViaCMHandler for: {}, {}", event.getData().getResourceId(), event);

        // TODO ZZZ Implement the actual decommission + Polling + populating a list for the next step.

        ClusterDownscaleVAltDecommissionViaCMResult result = new ClusterDownscaleVAltDecommissionViaCMResult(request);
        eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
    }
}
