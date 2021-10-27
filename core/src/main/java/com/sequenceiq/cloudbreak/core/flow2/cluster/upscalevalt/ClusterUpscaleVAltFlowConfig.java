package com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt;

import static com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt.ClusterUpscaleVAltEvents.*;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt.ClusterUpscaleVAltEvents.FAILURE_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt.ClusterUpscaleVAltState.*;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt.ClusterUpscaleVAltState.FINAL_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt.ClusterUpscaleVAltState.INIT_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt.ClusterUpscaleVAltState.UPSCALE_V_ALT_FAILED_STATE;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sequenceiq.flow.core.config.AbstractFlowConfiguration;
import com.sequenceiq.flow.core.config.RetryableFlowConfiguration;

@Component
public class ClusterUpscaleVAltFlowConfig extends AbstractFlowConfiguration<ClusterUpscaleVAltState, ClusterUpscaleVAltEvents> implements RetryableFlowConfiguration<ClusterUpscaleVAltEvents> {


    private static final List<Transition<ClusterUpscaleVAltState, ClusterUpscaleVAltEvents>> TRANSITIONS =
            new Transition.Builder<ClusterUpscaleVAltState, ClusterUpscaleVAltEvents>()
            .defaultFailureEvent(FAILURE_EVENT)
            .from(INIT_STATE)
                    .to(START_INSTANCE_STATE)
                    .event(CLUSTER_UPSCALE_VALT_TRIGGER_EVENT)
                    .defaultFailureEvent()
            .from(START_INSTANCE_STATE)
                    .to(CLUSTER_MANAGER_COMMISSION_STATE)
                    .event(CLUSTER_UPSCALE_NODES_STARTED_EVENT)
                    .defaultFailureEvent()
            .from(CLUSTER_MANAGER_COMMISSION_STATE)
                    .to(FINALIZE_UPSCALE_VALT_STATE)
                    .event(CLUSTER_UPSCALE_MANAGER_COMMISSIONED)
                    .defaultFailureEvent()
            .from(FINALIZE_UPSCALE_VALT_STATE)
                    .to(FINAL_STATE)
                    .event(FINALIZED_EVENT)
                    .defaultFailureEvent()
            .build();


    private static final FlowEdgeConfig<ClusterUpscaleVAltState, ClusterUpscaleVAltEvents> EDGE_CONFIG =
            new FlowEdgeConfig<>(INIT_STATE, FINAL_STATE, UPSCALE_V_ALT_FAILED_STATE, FAIL_HANDLED_EVENT);



    protected ClusterUpscaleVAltFlowConfig() {
        super(ClusterUpscaleVAltState.class, ClusterUpscaleVAltEvents.class);
    }

    @Override
    protected List<Transition<ClusterUpscaleVAltState, ClusterUpscaleVAltEvents>> getTransitions() {
        return TRANSITIONS;
    }

    @Override
    protected FlowEdgeConfig<ClusterUpscaleVAltState, ClusterUpscaleVAltEvents> getEdgeConfig() {
        return EDGE_CONFIG;
    }

    @Override
    public ClusterUpscaleVAltEvents[] getEvents() {
        return  ClusterUpscaleVAltEvents.values();
    }

    @Override
    public ClusterUpscaleVAltEvents[] getInitEvents() {
        return new ClusterUpscaleVAltEvents[]{CLUSTER_UPSCALE_VALT_TRIGGER_EVENT};
    }

    @Override
    public String getDisplayName() {
        return "Upscale Cluster V-Alt";
    }

    @Override
    public ClusterUpscaleVAltEvents getRetryableEvent() {
        return FAIL_HANDLED_EVENT;
    }
}
