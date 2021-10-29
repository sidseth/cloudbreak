package com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.AVAILABLE;
import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.UPDATE_FAILED;
import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.UPDATE_IN_PROGRESS;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_RE_REGISTER_WITH_CLUSTER_PROXY;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_SCALED_UP_VALT;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_SCALING_FAILED;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_SCALING_UP_VALT;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_SCALING_UP_VALT2;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.SCALING_VALT_NODES_STARTED;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.endpoint.v4.common.DetailedStackStatus;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.InstanceStatus;
import com.sequenceiq.cloudbreak.core.flow2.stack.CloudbreakFlowMessageService;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostGroup;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.domain.view.StackView;
import com.sequenceiq.cloudbreak.event.ResourceEvent;
import com.sequenceiq.cloudbreak.service.StackUpdater;
import com.sequenceiq.cloudbreak.service.cluster.ClusterService;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.cloudbreak.service.stack.InstanceMetaDataService;
import com.sequenceiq.cloudbreak.service.stack.flow.MetadataSetupService;

@Component
class ClusterUpscaleVAltFlowService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterUpscaleVAltFlowService.class);

    @Inject
    private ClusterService clusterService;

    @Inject
    private CloudbreakFlowMessageService flowMessageService;

    @Inject
    private StackUpdater stackUpdater;

    @Inject
    private HostGroupService hostGroupService;

    @Inject
    private InstanceMetaDataService instanceMetaDataService;

    @Inject
    private MetadataSetupService metadataSetupService;

    void startingInstances(long stackId, String hostGroupName, int nodeCount) {
        clusterService.updateClusterStatusByStackId(stackId, UPDATE_IN_PROGRESS,
                String.format("Scaling up (v-alt) host  group: %s", hostGroupName));
        flowMessageService.fireEventAndLog(stackId, UPDATE_IN_PROGRESS.name(), CLUSTER_SCALING_UP_VALT, hostGroupName, String.valueOf(nodeCount));
    }

    void instancesStarted(ClusterUpscaleVAltContext context, long stackId, List<InstanceMetaData> instancesStarted) {
        Stack stack = context.getStack();
        // TODO ZZZ2 ... this needs to be a new state. CREATED doesn't really make much sense.
        instancesStarted.stream().forEach(x -> instanceMetaDataService.updateInstanceStatus(x, InstanceStatus.CREATED));
        stackUpdater.updateStackStatus(stack.getId(), DetailedStackStatus.STARTED, "Instances: " + instancesStarted.size() + " started successfully.");
        flowMessageService.fireEventAndLog(stack.getId(), AVAILABLE.name(), SCALING_VALT_NODES_STARTED, String.valueOf(instancesStarted.size()), instancesStarted.stream().map(x -> x.getInstanceId()).collect(Collectors.toList()).toString());
    }

    void upscaleCommissionNewNodes(long stackId, String hostGroupName, List<String> instanceIds) {
        // TODO ZZZ2 - Update instance state to SERVICES_RUNNING at this point, rather than when the instances start up
        clusterService.updateClusterStatusByStackId(stackId, UPDATE_IN_PROGRESS,
                String.format("Commissioning via CM: %s", hostGroupName));
        flowMessageService.fireEventAndLog(stackId, UPDATE_IN_PROGRESS.name(), CLUSTER_SCALING_UP_VALT2, hostGroupName, String.valueOf(instanceIds.size()), instanceIds.toString());
    }

    void reRegisterWithClusterProxy(long stackId) {
        clusterService.updateClusterStatusByStackId(stackId, UPDATE_IN_PROGRESS, "Re-registering with Cluster Proxy service.");
        flowMessageService.fireEventAndLog(stackId, UPDATE_IN_PROGRESS.name(), CLUSTER_RE_REGISTER_WITH_CLUSTER_PROXY);
    }


    private void sendMessage(long stackId, ResourceEvent resourceEvent, String statusReason) {
        clusterService.updateClusterStatusByStackId(stackId, UPDATE_IN_PROGRESS, statusReason);
        flowMessageService.fireEventAndLog(stackId, UPDATE_IN_PROGRESS.name(), resourceEvent);
    }

    void clusterUpscaleFinished(StackView stackView, String hostgroupName, List<InstanceMetaData> commissioned) {
        int numOfFailedHosts = updateMetadata(stackView, hostgroupName);
        boolean success = numOfFailedHosts == 0;
        if (success) {
            LOGGER.debug("Cluster upscaled v-alt successfully");
            clusterService.updateClusterStatusByStackId(stackView.getId(), AVAILABLE);
            commissioned.stream().forEach(x -> instanceMetaDataService.updateInstanceStatus(x, InstanceStatus.SERVICES_RUNNING));
            stackUpdater.updateStackStatus(stackView.getId(), DetailedStackStatus.PROVISIONED, String.format("will this update the final state to available?"));
            flowMessageService.fireEventAndLog(stackView.getId(), AVAILABLE.name(), CLUSTER_SCALED_UP_VALT, hostgroupName);
        } else {
            LOGGER.debug("Cluster upscale failed v-alt. {} hosts failed to upscale", numOfFailedHosts);
            clusterService.updateClusterStatusByStackId(stackView.getId(), UPDATE_FAILED);
            flowMessageService.fireEventAndLog(stackView.getId(), UPDATE_FAILED.name(), CLUSTER_SCALING_FAILED, "added to",
                    String.format("Cluster upscale operation failed on %d node(s).", numOfFailedHosts));
        }
    }

    void clusterUpscaleFailed(long stackId, Exception errorDetails) {
        LOGGER.info("Error during Cluster upscale (v-alt) flow: " + errorDetails.getMessage(), errorDetails);
        clusterService.updateClusterStatusByStackId(stackId, UPDATE_FAILED, errorDetails.getMessage());
        stackUpdater.updateStackStatus(stackId, DetailedStackStatus.PROVISIONED,
                String.format("New node(s) could not be added to the cluster: %s", errorDetails));
        flowMessageService.fireEventAndLog(stackId, UPDATE_FAILED.name(), CLUSTER_SCALING_FAILED, "added to", errorDetails.getMessage());
    }

    private int updateMetadata(StackView stackView, String hostGroupName) {
        LOGGER.info("Start update metadata");
        Optional<HostGroup> hostGroupOptional = hostGroupService.getByClusterIdAndName(stackView.getClusterView().getId(), hostGroupName);
        if (hostGroupOptional.isPresent()) {
            Set<InstanceMetaData> notDeletedInstanceMetaDataSet = hostGroupOptional.get().getInstanceGroup().getNotDeletedInstanceMetaDataSet();
            return updateFailedHostMetaData(notDeletedInstanceMetaDataSet);
        } else {
            return 0;
        }
    }

    private int updateFailedHostMetaData(Collection<InstanceMetaData> instanceMetaData) {
        List<String> upscaleHostNames = getHostNames(instanceMetaData);
        Collection<String> successHosts = new HashSet<>(upscaleHostNames);
        return updateFailedHostMetaData(successHosts, instanceMetaData);
    }

    private int updateFailedHostMetaData(Collection<String> successHosts, Iterable<InstanceMetaData> instanceMetaDatas) {
        int failedHosts = 0;
        for (InstanceMetaData metaData : instanceMetaDatas) {
            if (!successHosts.contains(metaData.getDiscoveryFQDN())) {
                instanceMetaDataService.updateInstanceStatus(metaData, InstanceStatus.ORCHESTRATION_FAILED,
                        "Cluster upscale failed. Host does not have fqdn.");
                failedHosts++;
            }
        }
        return failedHosts;
    }

    private List<String> getHostNames(Collection<InstanceMetaData> instanceMetaData) {
        return instanceMetaData.stream().map(InstanceMetaData::getDiscoveryFQDN).collect(Collectors.toList());
    }
}
