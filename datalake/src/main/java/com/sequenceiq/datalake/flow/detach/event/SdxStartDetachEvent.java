package com.sequenceiq.datalake.flow.detach.event;

import com.sequenceiq.datalake.entity.SdxCluster;
import com.sequenceiq.datalake.flow.SdxEvent;
import com.sequenceiq.datalake.flow.detach.SdxDetachEvent;

public class SdxStartDetachEvent extends SdxEvent {

    private SdxCluster sdxCluster;

    public SdxStartDetachEvent(String selector, Long sdxId, SdxCluster newSdxCluster, String userId) {
        super(selector, sdxId, userId);
        sdxCluster = newSdxCluster;
    }

    public SdxCluster getSdxCluster() {
        return sdxCluster;
    }

    @Override
    public String selector() {
        return SdxDetachEvent.SDX_DETACH_EVENT.selector();
    }
}
