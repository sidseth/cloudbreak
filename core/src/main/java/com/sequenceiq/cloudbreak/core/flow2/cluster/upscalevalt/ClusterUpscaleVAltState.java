package com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt;

import com.sequenceiq.cloudbreak.core.flow2.restart.FillInMemoryStateStoreRestartAction;
import com.sequenceiq.flow.core.FlowState;
import com.sequenceiq.flow.core.RestartAction;

enum ClusterUpscaleVAltState implements FlowState {

    INIT_STATE,
    START_INSTANCE_STATE,
    CLUSTER_MANAGER_COMMISSION_STATE,
    FINALIZED_UPSCALE_STATE,
    UPSCALE_V_ALT_FAILED_STATE,
    FINAL_STATE;


    private final Class<? extends RestartAction> restartAction = FillInMemoryStateStoreRestartAction.class;

    public String boo() {
        return "";
    }

    @Override
    public Class<? extends RestartAction> restartAction() {
        return null;
    }
}
