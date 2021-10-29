package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.UPDATE_IN_PROGRESS;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_SCALING_DOWN_CMHOSTS_ENTERED_MAINT;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_SCALING_DOWN_CMHOSTS_ENTERING_MAINT;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.InstanceStatus;
import com.sequenceiq.cloudbreak.cluster.api.ClusterDecomissionService;
import com.sequenceiq.cloudbreak.common.exception.NotFoundException;
import com.sequenceiq.cloudbreak.core.flow2.stack.CloudbreakFlowMessageService;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.Cluster;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostGroup;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.logger.MDCUtils;
import com.sequenceiq.cloudbreak.perflogger.PerfLogger;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterDownscaleVAltDecommissionViaCMRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.ClusterDownscaleVAltDecommissionViaCMResult;
import com.sequenceiq.cloudbreak.service.cluster.ClusterApiConnectors;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.cloudbreak.service.stack.InstanceMetaDataService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.flow.reactor.api.handler.EventHandler;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class ClusterDownscaleVAltDecommissionViaCMHandler implements EventHandler<ClusterDownscaleVAltDecommissionViaCMRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterDownscaleVAltDecommissionViaCMHandler.class);

    @Inject
    private EventBus eventBus;

    @Inject
    private ClusterApiConnectors clusterApiConnectors;

    @Inject
    private StackService stackService;

    @Inject
    private HostGroupService hostGroupService;

    @Inject
    private InstanceMetaDataService instanceMetaDataService;

    // ZZZ Temporary:
    @Inject
    private CloudbreakFlowMessageService flowMessageService;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(ClusterDownscaleVAltDecommissionViaCMRequest.class);
    }

    // ZZZ Tons copied over from DecommissionHandler

    @Override
    public void accept(Event<ClusterDownscaleVAltDecommissionViaCMRequest> event) {
        ClusterDownscaleVAltDecommissionViaCMRequest request = event.getData();
        LOGGER.info("ZZZ: ClusterDownscaleVAltDecommissionViaCMHandler for: {}, {}", event.getData().getResourceId(), event);

        PerfLogger.get().opBegin(MDCUtils.getPerfContextString(), "ClusterDownscaleVAltDecommissionViaCMHandler.accept");

        try {
            Stack stack = request.getStack();
            Cluster cluster = stack.getCluster();
            ClusterDecomissionService clusterDecomissionService = clusterApiConnectors.getConnector(stack).clusterDecomissionService();

            Set<String> hostNames = getHostNamesForPrivateIds(request.getInstanceIdsToDecommission(), stack);
            LOGGER.info("ZZZ: hostNamesToDecommission: count={}, hostNames={}", hostNames.size(), hostNames);

            HostGroup hostGroup = hostGroupService.getByClusterIdAndName(cluster.getId(), request.getHostGroupName())
                    .orElseThrow(NotFoundException.notFound("hostgroup", request.getHostGroupName()));

            Map<String, InstanceMetaData> hostsToRemove = clusterDecomissionService.collectHostsToRemove(hostGroup, hostNames);
            LOGGER.info("ZZZ: hostNamesToDecommission after checking with CM: count={}, details={}", hostsToRemove.size(), hostsToRemove);

            Set<String> decommissionedHostNames = Collections.emptySet();
            if (hostsToRemove.size() > 0) {
                updateInstanceStatuses(hostsToRemove.values(), InstanceStatus.DECOMMISSIONED, "decommission requested for instance");
                decommissionedHostNames = clusterDecomissionService.decommissionClusterNodes(hostsToRemove);
            }
            LOGGER.info("ZZZ: hostsDecommissioned: count={}, hostNames={}", decommissionedHostNames.size(), decommissionedHostNames);

            LOGGER.info("ZZZ: Attempting to put hosts into maintenance mode");
            flowMessageService.fireEventAndLog(stack.getId(), UPDATE_IN_PROGRESS.name(), CLUSTER_SCALING_DOWN_CMHOSTS_ENTERING_MAINT, String.valueOf(hostsToRemove.size()));
            PerfLogger.get().opBegin(MDCUtils.getPerfContextString(), "ClusterDownscaleVAltDecommissionViaCMHandler.accept.maintmode");
            clusterDecomissionService.enterMaintenanceMode(stack, hostsToRemove);
            PerfLogger.get().opEnd__(MDCUtils.getPerfContextString(), "ClusterDownscaleVAltDecommissionViaCMHandler.accept.maintmode");
            flowMessageService.fireEventAndLog(stack.getId(), UPDATE_IN_PROGRESS.name(), CLUSTER_SCALING_DOWN_CMHOSTS_ENTERED_MAINT, String.valueOf(hostsToRemove.size()));
            LOGGER.info("ZZZ: Nodes moved to maintenance mode");


            ClusterDownscaleVAltDecommissionViaCMResult result = new ClusterDownscaleVAltDecommissionViaCMResult(request, decommissionedHostNames);
            eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
        } finally {
            PerfLogger.get().opEnd__(MDCUtils.getPerfContextString(), "ClusterDownscaleVAltDecommissionViaCMHandler.accept");
        }
    }

    private Set<String> getHostNamesForPrivateIds(Set<Long> hostIdsToRemove, Stack stack) {
        // List<String> decomissionedHostNames = stackService.getHostNamesForPrivateIds(stack.getInstanceMetaDataAsList(), request.getInstanceIdsToDecommission());
        return hostIdsToRemove.stream().map(privateId -> {
            Optional<InstanceMetaData> instanceMetadata = stackService.getInstanceMetadata(stack.getInstanceMetaDataAsList(), privateId);
            return instanceMetadata.map(InstanceMetaData::getDiscoveryFQDN).orElse(null);
        }).filter(StringUtils::isNotEmpty).collect(Collectors.toSet());
    }

    private void updateInstanceStatuses(Collection<InstanceMetaData> instanceMetadatas, InstanceStatus instanceStatus, String statusReason) {
        for (InstanceMetaData instanceMetaData : instanceMetadatas) {
            instanceMetaDataService.updateInstanceStatus(instanceMetaData, instanceStatus, statusReason);
        }
    }
}
