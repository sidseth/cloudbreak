package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterResult;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.flow.reactor.api.handler.EventHandler;

import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterRequest;

import reactor.bus.Event;
import reactor.bus.EventBus;

public class UpscaleClusterVAltHandler implements EventHandler<UpscaleClusterRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpscaleClusterVAltHandler.class);

    @Inject
    private EventBus eventBus;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(UpscaleClusterRequest.class);
    }

    @Override
    public void accept(Event<UpscaleClusterRequest> event) {
        UpscaleClusterRequest request = event.getData();
        LOGGER.info("ZZZ: UpscaleClusterHandler for alt scaling path: {}", event.getData().getResourceId());
        UpscaleClusterResult result = new UpscaleClusterResult(request);
        eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
    }
}
