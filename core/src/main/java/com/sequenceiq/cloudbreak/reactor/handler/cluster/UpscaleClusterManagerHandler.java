package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.core.cluster.ClusterUpscaleService;
import com.sequenceiq.cloudbreak.logger.MDCUtils;
import com.sequenceiq.cloudbreak.perflogger.PerfLogger;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleClusterManagerRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleClusterManagerResult;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.flow.reactor.api.handler.EventHandler;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class UpscaleClusterManagerHandler implements EventHandler<UpscaleClusterManagerRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpscaleClusterManagerHandler.class);

    @Inject
    private EventBus eventBus;

    @Inject
    private ClusterUpscaleService clusterUpscaleService;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(UpscaleClusterManagerRequest.class);
    }

    @Override
    public void accept(Event<UpscaleClusterManagerRequest> event) {
        UpscaleClusterManagerRequest request = event.getData();
        UpscaleClusterManagerResult result;
        LOGGER.debug("UpscaleClusterManagerHandler for {}", event.getData().getResourceId());
        try {
            PerfLogger.get().opBegin(MDCUtils.getPerfContextString(), "UpscaleClusterManagerHandler.accept");
            clusterUpscaleService.upscaleClusterManager(request.getResourceId(), request.getHostGroupName(),
                    request.getScalingAdjustment(), request.isPrimaryGatewayChanged());
            result = new UpscaleClusterManagerResult(request);
        } catch (Exception e) {
            result = new UpscaleClusterManagerResult(e.getMessage(), e, request);
        } finally {
            PerfLogger.get().opEnd__(MDCUtils.getPerfContextString(), "UpscaleClusterManagerHandler.accept");
        }
        eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
    }
}
