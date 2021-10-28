package com.sequenceiq.cloudbreak.core.flow2.cluster.downscalevalt;


import static com.sequenceiq.cloudbreak.cloud.model.AvailabilityZone.availabilityZone;
import static com.sequenceiq.cloudbreak.cloud.model.Location.location;
import static com.sequenceiq.cloudbreak.cloud.model.Region.region;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt.ClusterUpscaleVAltEvents.FINALIZED_EVENT;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;
import com.sequenceiq.cloudbreak.cloud.model.Location;
import com.sequenceiq.cloudbreak.common.event.Payload;
import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.cloudbreak.common.type.ClusterManagerType;
import com.sequenceiq.cloudbreak.converter.spi.StackToCloudStackConverter;
import com.sequenceiq.cloudbreak.core.flow2.AbstractStackAction;
import com.sequenceiq.cloudbreak.core.flow2.event.ClusterDownscaleVAltTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.stack.AbstractStackFailureAction;
import com.sequenceiq.cloudbreak.core.flow2.stack.StackFailureContext;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.reactor.api.event.StackFailureEvent;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterDownscaleVAltDecommissionViaCMRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterDownscaleVAltStopInstancesRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterUpscaleFailedConclusionRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.ClusterDownscaleVAltDecommissionViaCMResult;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.ClusterDownscaleVAltStopInstancesResult;
import com.sequenceiq.cloudbreak.service.metrics.MetricType;
import com.sequenceiq.cloudbreak.service.resource.ResourceService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.cloudbreak.util.StackUtil;
import com.sequenceiq.flow.core.FlowParameters;

@Configuration
public class ClusterDownscaleVAltActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterDownscaleVAltActions.class);

    @Inject
    private ClusterDownscaleVAltFlowService clusterDownscaleFlowService;


    @Bean(name = "CLUSTER_MANAGER_HOSTS_DECOMMISSION_STATE")
    public Action<?, ?> decommissionViaCmAction() {
        return new AbstractClusterDownscaleVAltActions<>(ClusterDownscaleVAltTriggerEvent.class) {

            @Override
            protected void prepareExecution(ClusterDownscaleVAltTriggerEvent payload, Map<Object, Object> variables) {
                variables.put(HOSTGROUPNAME, payload.getHostGroupName());
                variables.put(ADJUSTMENT, payload.getAdjustment());
                variables.put(SINGLE_PRIMARY_GATEWAY, payload.isSinglePrimaryGateway());
                variables.put(CLUSTER_MANAGER_TYPE, payload.getClusterManagerType());
                variables.put(RESTART_SERVICES, payload.isRestartServices());
                variables.put(HOSTS_TO_REMOVE, payload.getHostIds());
            }

            @Override
            protected void doExecute(ClusterDownscaleVAltContext context, ClusterDownscaleVAltTriggerEvent payload, Map<Object, Object> variables) throws Exception {
                clusterDownscaleFlowService.clusterDownscaleStarted(context.getStack().getId(), payload.getHostGroupName(), payload.getAdjustment(), payload.getHostIds());
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(ClusterDownscaleVAltContext context) {
                return new ClusterDownscaleVAltDecommissionViaCMRequest(context.getStack().getId(), context.getHostGroupName(), context.getHostIdsToRemove());
            }
        };
    }

    @Bean(name = "STOP_INSTANCE_STATE")
    public Action<?, ?> stopInstancesAction() {
        return new AbstractClusterDownscaleVAltActions<>(ClusterDownscaleVAltDecommissionViaCMResult.class) {

            @Override
            protected void doExecute(ClusterDownscaleVAltContext context, ClusterDownscaleVAltDecommissionViaCMResult payload, Map<Object, Object> variables) throws Exception {
                Stack stack = context.getStack();
                // TODO ZZZ: This needs to come from the previous step (payload) once it is implemented - i.e. the getHostIdsToRemove
                clusterDownscaleFlowService.clusterDownscalingStoppingInstances(stack.getId(), context.getHostGroupName(), context.getHostIdsToRemove());

                List<InstanceMetaData> instanceMetaDataList = stack.getNotDeletedInstanceMetaDataList();
                LOGGER.info("ZZZ: AllInstances: count={}, instances={}", instanceMetaDataList.size(), instanceMetaDataList);
                List<InstanceMetaData> instanceMetaDataForHg = instanceMetaDataList.stream().filter(x -> x.getInstanceGroupName().equals(context.getHostGroupName())).collect(Collectors.toList());
                LOGGER.info("ZZZ: The following instnaces were found in the required hostGroup: {}", instanceMetaDataForHg);

                // TODO ZZZ: This needs to come from the previous step (payload) once it is implemented - i.e. the getHostIdsToRemove
                ClusterDownscaleVAltStopInstancesRequest request = new ClusterDownscaleVAltStopInstancesRequest(context.getCloudContext(), context.getCloudCredential(), context.getCloudStack(), context.getStack(),
                        context.getHostGroupName(), instanceMetaDataForHg, context.getHostIdsToRemove());

                sendEvent(context, request);
            }
        };
    }

    @Bean(name = "FINALIZE_DOWNSCALE_V_ALT_STATE")
    public Action<?, ?> downscaleFinishedAction() {
        return new AbstractClusterDownscaleVAltActions<>(ClusterDownscaleVAltStopInstancesResult.class) {

            @Override
            protected void doExecute(ClusterDownscaleVAltContext context, ClusterDownscaleVAltStopInstancesResult payload, Map<Object, Object> variables) throws Exception {
                LOGGER.info("ZZZ: Marking Downscale v-alt as finished");

                clusterDownscaleFlowService.clusterDownscaleFinished(context.getStack().getId(), context.getHostGroupName(), new HashSet<>(payload.getToStopInstanceMetadata()));
                getMetricService().incrementMetricCounter(MetricType.CLUSTER_UPSCALE_SUCCESSFUL, context.getStack());
                sendEvent(context, FINALIZED_EVENT.event(), payload);
            }

            @Override
            protected Selectable createRequest(ClusterDownscaleVAltContext context) {
                return null;
            }
        };
    }


    @Bean(name = "DOWNSCALE_V_ALT_FAILED_STATE")
    public Action<?, ?> clusterDownscaleFailedAction() {
        return new AbstractStackFailureAction<ClusterDownscaleVAltState, ClusterDownscaleVAltEvents>() {

            @Override
            protected void doExecute(StackFailureContext context, StackFailureEvent payload, Map<Object, Object> variables) {
                clusterDownscaleFlowService.handleClusterDownscaleFailure(context.getStackView().getId(), payload.getException());
                getMetricService().incrementMetricCounter(MetricType.CLUSTER_UPSCALE_FAILED, context.getStackView(), payload.getException());
                // ZZZ : this is broken - here, and in the upscale part.
                // TODO ZZZ2: What gets persisted to the DB (stack vs instance, actual states, instanceDetails STOPPED vs STARTED, etc needs to be looked at in a LOT more detail)
                ClusterUpscaleFailedConclusionRequest request = new ClusterUpscaleFailedConclusionRequest(context.getStackView().getId());
                sendEvent(context, request.selector(), request);
            }
        };
    }










    private abstract static class AbstractClusterDownscaleVAltActions<P extends Payload>
            extends AbstractStackAction<ClusterDownscaleVAltState, ClusterDownscaleVAltEvents, ClusterDownscaleVAltContext, P> {

        static final String HOSTGROUPNAME = "HOSTGROUPNAME";

        static final String HOSTS_TO_REMOVE = "HOSTS_TO_REMOVE";

        static final String ADJUSTMENT = "ADJUSTMENT";

        static final String SINGLE_PRIMARY_GATEWAY = "SINGLE_PRIMARY_GATEWAY";

        static final String INSTALLED_COMPONENTS = "INSTALLED_COMPONENTS";

        static final String CLUSTER_MANAGER_TYPE = "CLUSTER_MANAGER_TYPE";

        static final String RESTART_SERVICES = "RESTART_SERVICES";

        static final String REPAIR = "REPAIR";

        @Inject
        private StackService stackService;

        @Inject
        private ResourceService resourceService;

        @Inject
        private StackUtil stackUtil;

        @Inject
        private StackToCloudStackConverter cloudStackConverter;

        public AbstractClusterDownscaleVAltActions(Class<P> payloadClass) {
            super(payloadClass);
        }

        @Override
        protected Object getFailurePayload(P payload, Optional<ClusterDownscaleVAltContext> flowContext, Exception ex) {
            return new StackFailureEvent(payload.getResourceId(), ex);
        }

        @Override
        protected ClusterDownscaleVAltContext createFlowContext(FlowParameters flowParameters, StateContext<ClusterDownscaleVAltState,
                ClusterDownscaleVAltEvents> stateContext, P payload) {

            Map<Object, Object> variables = stateContext.getExtendedState().getVariables();
            Stack stack = stackService.getByIdWithListsInTransaction(payload.getResourceId());
            stack.setResources(new HashSet<>(resourceService.getAllByStackId(payload.getResourceId())));
            MDCBuilder.buildMdcContext(stack.getCluster()); // ZZZ: Is this OK to do? In a stack operation.
            Location location = location(region(stack.getRegion()), availabilityZone(stack.getAvailabilityZone()));


            CloudContext cloudContext = CloudContext.Builder.builder()
                    .withId(stack.getId())
                    .withName(stack.getName())
                    .withCrn(stack.getResourceCrn())
                    .withPlatform(stack.getCloudPlatform())
                    .withVariant(stack.getPlatformVariant())
                    .withLocation(location)
                    .withWorkspaceId(stack.getWorkspace().getId())
                    .withAccountId(stack.getTenant().getId())
                    .build();
            CloudCredential cloudCredential = stackUtil.getCloudCredential(stack);
            CloudStack cloudStack = cloudStackConverter.convert(stack);

            return new ClusterDownscaleVAltContext(flowParameters, stack, stackService.getViewByIdWithoutAuth(stack.getId()), cloudContext, cloudCredential, cloudStack,
                    getHostgroupName(variables), getHostsToRemove(variables), getAdjustment(variables),
                    isSinglePrimaryGateway(variables), getClusterManagerType(variables), isRestartServices(variables));
        }

        private String getHostgroupName(Map<Object, Object> variables) {
            return (String) variables.get(HOSTGROUPNAME);
        }

        Set<Long> getHostsToRemove(Map<Object, Object> variables) {
            return (Set<Long>) variables.get(HOSTS_TO_REMOVE);
        }

        private Integer getAdjustment(Map<Object, Object> variables) {
            return (Integer) variables.get(ADJUSTMENT);
        }

        private Boolean isSinglePrimaryGateway(Map<Object, Object> variables) {
            return (Boolean) variables.get(SINGLE_PRIMARY_GATEWAY);
        }

        Map<String, String> getInstalledComponents(Map<Object, Object> variables) {
            return (Map<String, String>) variables.get(INSTALLED_COMPONENTS);
        }

        Boolean isRepair(Map<Object, Object> variables) {
            return (Boolean) variables.get(REPAIR);
        }

        Boolean isRestartServices(Map<Object, Object> variables) {
            return (Boolean) variables.get(RESTART_SERVICES);
        }

        ClusterManagerType getClusterManagerType(Map<Object, Object> variables) {
            return (ClusterManagerType) variables.get(CLUSTER_MANAGER_TYPE);
        }
    }
}
