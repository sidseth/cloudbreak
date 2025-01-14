package com.sequenceiq.datalake.flow.detach;

import com.sequenceiq.datalake.flow.FillInMemoryStateStoreRestartAction;
import com.sequenceiq.flow.core.FlowState;
import com.sequenceiq.flow.core.RestartAction;
import com.sequenceiq.flow.core.restart.DefaultRestartAction;

public enum SdxDetachState implements FlowState {

    INIT_STATE,
    SDX_DETACH_START_STATE,
    SDX_DETACH_IN_PROGRESS_STATE,
    SDX_DETACH_FAILED_STATE,
    SDX_DETACH_FINISHED_STATE,
    FINAL_STATE;

    private Class<? extends DefaultRestartAction> restartAction = FillInMemoryStateStoreRestartAction.class;

    SdxDetachState() {
    }

    SdxDetachState(Class<? extends DefaultRestartAction> restartAction) {
        this.restartAction = restartAction;
    }

    @Override
    public Class<? extends RestartAction> restartAction() {
        return restartAction;
    }

}
