package com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt;

import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.ClusterDownscaleVAltDecommissionViaCMResult;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.ClusterDownscaleVAltStopInstancesResult;
import com.sequenceiq.flow.core.FlowEvent;
import com.sequenceiq.flow.event.EventSelectorUtil;

public enum ClusterDownscaleVAltEvents implements FlowEvent {

    CLUSTER_DOWNSCALE_V_ALT_TRIGGER_EVENT("CLUSTER_DOWNSCALE_V_ALT_TRIGGER_EVENT"),
    CLUSTER_DOWNSCALE_V_ALT_CLUSTER_MANAGER_DECOMMISSIONED_EVENT(EventSelectorUtil.selector(ClusterDownscaleVAltDecommissionViaCMResult.class)),
    CLUSTER_DOWNSCALE_V_ALT_INSTANCES_STOPPED_EVENT(EventSelectorUtil.selector(ClusterDownscaleVAltStopInstancesResult.class)),
    FINALIZED_EVENT("CLUSTER_DOWNSCALE_V_ALT_FINALIZED_EVENT"),
    FAILURE_EVENT("CLUSTER_DOWNSCALE_V_ALT_FAILURE_EVENT"),
    FAIL_HANDLE_EVENT("CLUSTER_DOWNSCALE_V_ALT_FAIL_HANDLED_EVENT");



    private final String event;

    ClusterDownscaleVAltEvents(String event) {
        this.event = event;
    }

    @Override
    public String event() {
        return event;
    }
}
