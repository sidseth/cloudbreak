package com.sequenceiq.cloudbreak.reactor.handler.recipe;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.core.cluster.ClusterUpscaleService;
import com.sequenceiq.cloudbreak.logger.MDCUtils;
import com.sequenceiq.cloudbreak.perflogger.PerfLogger;
import com.sequenceiq.cloudbreak.reactor.api.event.recipe.UpscalePostRecipesRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.recipe.UpscalePostRecipesResult;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.flow.reactor.api.handler.EventHandler;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class UpscalePostRecipesHandler implements EventHandler<UpscalePostRecipesRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpscalePostRecipesHandler.class);

    @Inject
    private EventBus eventBus;

    @Inject
    private ClusterUpscaleService clusterUpscaleService;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(UpscalePostRecipesRequest.class);
    }

    @Override
    public void accept(Event<UpscalePostRecipesRequest> event) {

        LOGGER.debug("ZZZ: UpscalePostRecipesRequest");
        UpscalePostRecipesRequest request = event.getData();
        UpscalePostRecipesResult result;
        PerfLogger.get().opBegin(MDCUtils.getPerfContextString(), "UpscalePostRecipesRequest");
        try {
            clusterUpscaleService.executePostRecipesOnNewHosts(request.getResourceId());
            result = new UpscalePostRecipesResult(request);
        } catch (Exception e) {
            result = new UpscalePostRecipesResult(e.getMessage(), e, request);
        } finally {
            PerfLogger.get().opEnd__(MDCUtils.getPerfContextString(), "UpscalePostRecipesRequest");
        }
        eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
    }
}
