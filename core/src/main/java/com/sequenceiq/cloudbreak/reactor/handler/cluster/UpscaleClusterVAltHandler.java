package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterVAltRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleVAltStartInstancesResult;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.flow.reactor.api.handler.EventHandler;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class UpscaleClusterVAltHandler implements EventHandler<UpscaleClusterVAltRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpscaleClusterVAltHandler.class);

    @Inject
    private EventBus eventBus;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(UpscaleClusterVAltRequest.class);
    }

    @Override
    public void accept(Event<UpscaleClusterVAltRequest> event) {
        UpscaleClusterVAltRequest request = event.getData();
        LOGGER.info("ZZZ: UpscaleClusterHandler for alt scaling path: {}", event.getData().getResourceId());
        UpscaleVAltStartInstancesResult result = new UpscaleVAltStartInstancesResult(request.getResourceId());
        eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
    }
}
