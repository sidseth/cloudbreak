package com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt;

import com.sequenceiq.cloudbreak.core.flow2.restart.FillInMemoryStateStoreRestartAction;
import com.sequenceiq.flow.core.FlowState;
import com.sequenceiq.flow.core.RestartAction;

enum ClusterDownscaleVAltState implements FlowState {

    INIT_STATE,
    CLUSTER_MANAGER_HOSTS_DECOMMISSION_STATE,
    STOP_INSTANCE_STATE,
    FINALIZE_DOWNSCALE_V_ALT_STATE,
    DOWNSCALE_V_ALT_FAILED_STATE,
    FINAL_STATE;

    private final Class<? extends RestartAction> restartAction = FillInMemoryStateStoreRestartAction.class;

    @Override
    public Class<? extends RestartAction> restartAction() {
        return restartAction;
    }
}
