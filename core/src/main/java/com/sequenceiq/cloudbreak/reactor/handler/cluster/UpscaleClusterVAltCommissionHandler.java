package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterVAltCommissionViaCMRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleClusterVAltCommissionViaCMResult;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.flow.reactor.api.handler.EventHandler;

import reactor.bus.Event;


public class UpscaleClusterVAltCommissionHandler implements EventHandler<UpscaleClusterVAltCommissionViaCMRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpscaleClusterVAltCommissionHandler.class);

    @Override
    public String selector() {
        return EventSelectorUtil.selector(UpscaleClusterVAltCommissionViaCMRequest.class);
    }

    @Override
    public void accept(Event<UpscaleClusterVAltCommissionViaCMRequest> event) {
        UpscaleClusterVAltCommissionViaCMRequest request = event.getData();
        LOGGER.info("ZZZ: UpscaleClusterVAltCommissionHandler for: {}, {}", event.getData().getResourceId(), event);
        UpscaleClusterVAltCommissionViaCMResult result = new UpscaleClusterVAltCommissionViaCMResult(request);
    }
}
