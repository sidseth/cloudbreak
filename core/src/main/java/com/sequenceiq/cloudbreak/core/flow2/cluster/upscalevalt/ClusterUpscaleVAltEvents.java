package com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt;

import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleClusterVAltCommissionViaCMResult;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleVAltStartInstancesResult;
import com.sequenceiq.flow.core.FlowEvent;
import com.sequenceiq.flow.event.EventSelectorUtil;

public enum ClusterUpscaleVAltEvents implements FlowEvent {

    CLUSTER_UPSCALE_VALT_TRIGGER_EVENT("CLUSTER_UPSCALE_VALT_TRIGGER_EVENT"),
    CLUSTER_UPSCALE_NODES_STARTED_EVENT(EventSelectorUtil.selector(UpscaleVAltStartInstancesResult.class)),
    CLUSTER_UPSCALE_MANAGER_COMMISSIONED(EventSelectorUtil.selector(UpscaleClusterVAltCommissionViaCMResult.class)),
    FINALIZED_EVENT("CLUSTER_UPSCALE_VALT_FINALIZED_EVENT"),
    FAILURE_EVENT("CLUSTER_UPSCALE_VALT_FAILURE_EVENT"),
    FAIL_HANDLED_EVENT("CLUSTER_UPSCALE_VALT_FAIL_HANDLED_EVENT");


    private final String event;

    ClusterUpscaleVAltEvents(String event) {
        this.event = event;
    }

    @Override
    public String event() {
        return event;
    }
}
