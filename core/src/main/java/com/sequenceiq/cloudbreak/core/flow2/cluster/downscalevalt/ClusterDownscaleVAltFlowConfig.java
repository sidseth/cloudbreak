package com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt;

import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltEvents.CLUSTER_DOWNSCALE_V_ALT_CLUSTER_MANAGER_DECOMMISSIONED_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltEvents.CLUSTER_DOWNSCALE_V_ALT_INSTANCES_STOPPED_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltEvents.CLUSTER_DOWNSCALE_V_ALT_TRIGGER_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltEvents.FAILURE_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltEvents.FAIL_HANDLE_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltEvents.FINALIZED_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltState.CLUSTER_MANAGER_HOSTS_DECOMMISSION_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltState.DOWNSCALE_V_ALT_FAILED_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltState.FINALIZE_DOWNSCALE_V_ALT_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltState.FINAL_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltState.INIT_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltState.STOP_INSTANCE_STATE;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sequenceiq.flow.core.config.AbstractFlowConfiguration;
import com.sequenceiq.flow.core.config.RetryableFlowConfiguration;

@Component
public class ClusterDownscaleVAltFlowConfig extends AbstractFlowConfiguration<ClusterDownscaleVAltState, ClusterDownscaleVAltEvents> implements RetryableFlowConfiguration<ClusterDownscaleVAltEvents> {

    private static final List<Transition<ClusterDownscaleVAltState, ClusterDownscaleVAltEvents>> TRANSITIONS =
            new Transition.Builder<ClusterDownscaleVAltState, ClusterDownscaleVAltEvents>()
            .defaultFailureEvent(FAILURE_EVENT)
            .from(ClusterDownscaleVAltState.INIT_STATE)
                .to(CLUSTER_MANAGER_HOSTS_DECOMMISSION_STATE)
                .event(CLUSTER_DOWNSCALE_V_ALT_TRIGGER_EVENT)
                .defaultFailureEvent()
            .from(CLUSTER_MANAGER_HOSTS_DECOMMISSION_STATE)
                .to(STOP_INSTANCE_STATE)
                .event(CLUSTER_DOWNSCALE_V_ALT_CLUSTER_MANAGER_DECOMMISSIONED_EVENT)
                .defaultFailureEvent()
            .from(STOP_INSTANCE_STATE)
                .to(FINALIZE_DOWNSCALE_V_ALT_STATE)
                .event(CLUSTER_DOWNSCALE_V_ALT_INSTANCES_STOPPED_EVENT)
                .defaultFailureEvent()
            .from(FINALIZE_DOWNSCALE_V_ALT_STATE)
                .to(FINAL_STATE)
                .event(FINALIZED_EVENT)
                .defaultFailureEvent()
            .build();

    private static final FlowEdgeConfig<ClusterDownscaleVAltState, ClusterDownscaleVAltEvents> EDGE_CONFIG =
            new FlowEdgeConfig<>(INIT_STATE, FINAL_STATE, DOWNSCALE_V_ALT_FAILED_STATE, FAIL_HANDLE_EVENT);


    protected ClusterDownscaleVAltFlowConfig() {
        super(ClusterDownscaleVAltState.class, ClusterDownscaleVAltEvents.class);
    }

    @Override
    protected List<Transition<ClusterDownscaleVAltState, ClusterDownscaleVAltEvents>> getTransitions() {
        return TRANSITIONS;
    }

    @Override
    protected FlowEdgeConfig<ClusterDownscaleVAltState, ClusterDownscaleVAltEvents> getEdgeConfig() {
        return EDGE_CONFIG;
    }

    @Override
    public ClusterDownscaleVAltEvents[] getEvents() {
        return ClusterDownscaleVAltEvents.values();
    }

    @Override
    public ClusterDownscaleVAltEvents[] getInitEvents() {
        return new ClusterDownscaleVAltEvents[]{CLUSTER_DOWNSCALE_V_ALT_TRIGGER_EVENT};
    }

    @Override
    public String getDisplayName() {
        return "Downscale Cluster V-Alt";
    }

    @Override
    public ClusterDownscaleVAltEvents getRetryableEvent() {
        return FAIL_HANDLE_EVENT;
    }
}
