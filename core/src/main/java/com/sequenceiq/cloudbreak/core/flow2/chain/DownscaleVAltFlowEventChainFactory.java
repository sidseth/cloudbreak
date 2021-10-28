package com.sequenceiq.cloudbreak.core.flow2.chain;

import static com.sequenceiq.cloudbreak.core.flow2.stack.sync.StackSyncEvent.STACK_SYNC_EVENT;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.google.common.collect.Sets;
import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.cloudbreak.common.exception.NotFoundException;
import com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt.ClusterDownscaleVAltEvents;
import com.sequenceiq.cloudbreak.core.flow2.event.ClusterAndStackDownscaleTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.event.ClusterDownscaleVAltTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.event.StackSyncTriggerEvent;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostGroup;
import com.sequenceiq.cloudbreak.domain.view.ClusterView;
import com.sequenceiq.cloudbreak.domain.view.StackView;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.flow.core.chain.FlowEventChainFactory;
import com.sequenceiq.flow.core.chain.config.FlowTriggerEventQueue;

@Component
public class DownscaleVAltFlowEventChainFactory implements FlowEventChainFactory<ClusterAndStackDownscaleTriggerEvent> {

    @Inject
    private StackService stackService;

    @Inject
    private HostGroupService hostGroupService;

    @Override
    public String initEvent() {
        return FlowChainTriggers.FULL_DOWNSCALE_TRIGGER_V_ALT_EVENT;
    }

    @Override
    public FlowTriggerEventQueue createFlowTriggerEventQueue(ClusterAndStackDownscaleTriggerEvent event) {

        StackView stackView = stackService.getViewByIdWithoutAuth(event.getResourceId());
        ClusterView clusterView = stackView.getClusterView();
        HostGroup hostGroup = hostGroupService.getByClusterIdAndName(clusterView.getId(), event.getHostGroupName())
                .orElseThrow(NotFoundException.notFound("hostgroup", event.getHostGroupName()));



        ClusterDownscaleVAltTriggerEvent te = new ClusterDownscaleVAltTriggerEvent(
                ClusterDownscaleVAltEvents.CLUSTER_DOWNSCALE_V_ALT_TRIGGER_EVENT.event(),
                stackView.getId(),
                hostGroup.getName(),
                event.getAdjustment(),
                // ZZZ This is sub-optimal. Will need to lookup the hostnames again in a subsequent operation.
                Sets.newHashSet(event.getPrivateIds()),
                event.isSinglePrimaryGateway(),
                event.isRestartServices(),
                event.getClusterManagerType()
        );

        Queue<Selectable> flowEventChain = new ConcurrentLinkedQueue<>();

        // ZZZ: Adding this temporarily, just to make sure the event gets accepted. The flow is otherwise not being accepted.
        addStackSyncTriggerEvent(event, flowEventChain);

        flowEventChain.add(te);

        return new FlowTriggerEventQueue(getName(), event, flowEventChain);
    }

    private void addStackSyncTriggerEvent(ClusterAndStackDownscaleTriggerEvent event, Queue<Selectable> flowEventChain) {
        flowEventChain.add(new StackSyncTriggerEvent(
                STACK_SYNC_EVENT.event(),
                event.getResourceId(),
                false,
                event.accepted())
        );
    }
}
