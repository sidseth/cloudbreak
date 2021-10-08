package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.core.cluster.ClusterUpscaleService;
import com.sequenceiq.cloudbreak.logger.MDCUtils;
import com.sequenceiq.cloudbreak.perflogger.PerfLogger;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterResult;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.flow.reactor.api.handler.EventHandler;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class UpscaleClusterHandler implements EventHandler<UpscaleClusterRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpscaleClusterHandler.class);

    @Inject
    private EventBus eventBus;

    @Inject
    private ClusterUpscaleService clusterUpscaleService;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(UpscaleClusterRequest.class);
    }

    @Override
    public void accept(Event<UpscaleClusterRequest> event) {
        UpscaleClusterRequest request = event.getData();
        UpscaleClusterResult result;
        LOGGER.debug("UpscaleClusterHandler for {}", event.getData().getResourceId());
        LOGGER.debug("ZZZ: UpscaleClusterHandler for {}", event.getData().getResourceId());
        try {
            PerfLogger.get().opBegin(MDCUtils.getPerfContextString(), "UpscaleClusterHandler.accept");
            clusterUpscaleService.installServicesOnNewHosts(request.getResourceId(), request.getHostGroupName(),
                    request.isRepair(), request.isRestartServices());
            result = new UpscaleClusterResult(request);
        } catch (Exception e) {
            result = new UpscaleClusterResult(e.getMessage(), e, request);
        } finally {
            PerfLogger.get().opEnd__(MDCUtils.getPerfContextString(), "UpscaleClusterHandler.accept");
        }
        eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
    }
}
