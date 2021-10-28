package com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.AVAILABLE;
import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.UPDATE_FAILED;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_REMOVING_NODE_FROM_HOSTGROUP2_VALT;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_REMOVING_NODE_FROM_HOSTGROUP_VALT;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_SCALED_DOWN_VALT;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_SCALING_DOWN_VALT;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_SCALING_FAILED;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_STOPPING_NODE_FROM_HOSTGROUP2_VALT;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.sequenceiq.cloudbreak.api.endpoint.v4.common.DetailedStackStatus;
import com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.InstanceStatus;
import com.sequenceiq.cloudbreak.core.flow2.stack.CloudbreakFlowMessageService;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.domain.view.StackView;
import com.sequenceiq.cloudbreak.service.StackUpdater;
import com.sequenceiq.cloudbreak.service.cluster.ClusterService;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.cloudbreak.service.stack.InstanceMetaDataService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.cloudbreak.service.stack.flow.MetadataSetupService;
import com.sun.istack.Nullable;

@Component
public class ClusterDownscaleVAltFlowService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterDownscaleVAltFlowService.class);


    @Inject
    private StackService stackService;

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


    public void clusterDownscaleStarted(long stackId, String hostGroupName, Integer scalingAdjustment, Set<Long> privateIds) {
        flowMessageService.fireEventAndLog(stackId, Status.UPDATE_IN_PROGRESS.name(), CLUSTER_SCALING_DOWN_VALT, hostGroupName);
        clusterService.updateClusterStatusByStackId(stackId, Status.UPDATE_IN_PROGRESS);
        if (scalingAdjustment != null && scalingAdjustment != 0) {
            LOGGER.info("ZZZ: Decommissioning v-alt {} hosts from host group '{}'", Math.abs(scalingAdjustment), hostGroupName);
            flowMessageService.fireInstanceGroupEventAndLog(stackId, Status.UPDATE_IN_PROGRESS.name(), hostGroupName, CLUSTER_REMOVING_NODE_FROM_HOSTGROUP_VALT,
                    String.valueOf(Math.abs(scalingAdjustment)), hostGroupName);
        } else if (!CollectionUtils.isEmpty(privateIds)) {
            LOGGER.info("ZZZ: Decommissioning v-alt {} hosts from host group '{}'", privateIds, hostGroupName);
            Stack stack = stackService.getByIdWithListsInTransaction(stackId);
            List<String> decomissionedHostNames = stackService.getHostNamesForPrivateIds(stack.getInstanceMetaDataAsList(), privateIds);
            flowMessageService.fireInstanceGroupEventAndLog(stackId, Status.UPDATE_IN_PROGRESS.name(), hostGroupName, CLUSTER_REMOVING_NODE_FROM_HOSTGROUP2_VALT,
                    String.valueOf(decomissionedHostNames.size()), String.join(",", decomissionedHostNames), hostGroupName);
        }
    }

    public void clusterDownscalingStoppingInstances(long stackId, String hostGroupName, Set<Long> privateIds) {
        LOGGER.info("ZZZ: Attempting to stop nodes v-alt {} from host group {}", privateIds, hostGroupName);
        Stack stack = stackService.getByIdWithListsInTransaction(stackId);
        List<String> decomissionedHostNames = stackService.getHostNamesForPrivateIds(stack.getInstanceMetaDataAsList(), privateIds);
        flowMessageService.fireInstanceGroupEventAndLog(stackId, Status.UPDATE_IN_PROGRESS.name(), hostGroupName, CLUSTER_STOPPING_NODE_FROM_HOSTGROUP2_VALT,
                String.valueOf(decomissionedHostNames.size()), String.join(",", decomissionedHostNames), hostGroupName);
    }

    public void clusterDownscaleFinished(Long stackId, @Nullable String hostGroupName, Set<InstanceMetaData> instancesStopped) {
        // TODO: Where is the state of the cloud provider instances being updated?
        StackView stackView = stackService.getViewByIdWithoutAuth(stackId);
        instancesStopped.stream().forEach(x -> instanceMetaDataService.updateInstanceStatus(x, InstanceStatus.STOPPED));
        stackUpdater.updateStackStatus(stackId, DetailedStackStatus.AVAILABLE, "Instances: " + instancesStopped.size() + " stopped successfully.");
        clusterService.updateClusterStatusByStackId(stackView.getId(), AVAILABLE);

        flowMessageService.fireEventAndLog(stackId, AVAILABLE.name(), CLUSTER_SCALED_DOWN_VALT, hostGroupName == null ? "null" : hostGroupName);
    }

    public void handleClusterDownscaleFailure(long stackId, Exception errorDetails) {
        LOGGER.info("Error during Cluster downscale (v-alt) flow: " + errorDetails.getMessage(), errorDetails);
        clusterService.updateClusterStatusByStackId(stackId, UPDATE_FAILED, errorDetails.getMessage());
        stackUpdater.updateStackStatus(stackId, DetailedStackStatus.AVAILABLE,
                String.format("New node(s) v-alt could not be removed from the cluster: %s", errorDetails));
        flowMessageService.fireEventAndLog(stackId, UPDATE_FAILED.name(), CLUSTER_SCALING_FAILED, "removed to", errorDetails.getMessage());
    }
}
