package com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt;


import static com.sequenceiq.cloudbreak.cloud.model.AvailabilityZone.availabilityZone;
import static com.sequenceiq.cloudbreak.cloud.model.Location.location;
import static com.sequenceiq.cloudbreak.cloud.model.Region.region;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt.ClusterUpscaleVAltEvents.FINALIZED_EVENT;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.sequenceiq.cloudbreak.core.flow2.event.ClusterScaleVAltTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.stack.AbstractStackFailureAction;
import com.sequenceiq.cloudbreak.core.flow2.stack.StackFailureContext;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.reactor.api.event.StackFailureEvent;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterUpscaleFailedConclusionRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterVAltCommissionViaCMRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterVAltRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleClusterVAltCommissionViaCMResult;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleVAltStartInstancesResult;
import com.sequenceiq.cloudbreak.service.metrics.MetricType;
import com.sequenceiq.cloudbreak.service.resource.ResourceService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.cloudbreak.util.StackUtil;
import com.sequenceiq.flow.core.FlowParameters;

@Configuration
public class ClusterUpscaleVAltActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterUpscaleVAltActions.class);

    @Inject
    private ClusterUpscaleVAltFlowService clusterUpscaleFlowService;

    @Bean(name = "START_INSTANCE_STATE")
    public Action<?, ?> startInstancesAction() {
        return new AbstractClusterUpscaleVAltActions<>(ClusterScaleVAltTriggerEvent.class) {

            @Override
            protected void prepareExecution(ClusterScaleVAltTriggerEvent payload, Map<Object, Object> variables) {
                variables.put(HOSTGROUPNAME, payload.getHostGroupName());
                variables.put(ADJUSTMENT, payload.getAdjustment());
                variables.put(SINGLE_PRIMARY_GATEWAY, payload.isSinglePrimaryGateway());
                variables.put(CLUSTER_MANAGER_TYPE, payload.getClusterManagerType());
                variables.put(RESTART_SERVICES, payload.isRestartServices());
                if (payload.isSinglePrimaryGateway()) {
                    variables.put(HOST_NAME, getMasterHostname(payload));
                }
            }

            private String getMasterHostname(ClusterScaleVAltTriggerEvent payload) {
                return payload.getHostNames().iterator().hasNext()
                        ? payload.getHostNames().iterator().next()
                        : "";
            }

            @Override
            protected void doExecute(ClusterUpscaleVAltContext context, ClusterScaleVAltTriggerEvent payload, Map<Object, Object> variables) throws Exception {
                clusterUpscaleFlowService.startingInstances(context.getStack().getId(), payload.getHostGroupName());
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(ClusterUpscaleVAltContext context) {

                Stack stack = context.getStack();
                List<InstanceMetaData> instanceMetaDataList = stack.getNotDeletedInstanceMetaDataList();
                LOGGER.info("ZZZ: AllInstances: count={}, instances={}", instanceMetaDataList.size(), instanceMetaDataList);
                List<InstanceMetaData> instanceMetaDataForHg = instanceMetaDataList.stream().filter(x -> x.getInstanceGroupName().equals(context.getHostGroupName())).collect(Collectors.toList());
                LOGGER.info("ZZZ: The following instnaces were found in the required hostGroup: {}", instanceMetaDataForHg);

                return new UpscaleClusterVAltRequest(context.getCloudContext(), context.getCloudCredential(), context.getCloudStack(), context.getStack(),
                        context.getHostGroupName(), context.getAdjustment(), instanceMetaDataForHg);
            }
        };
    }

    @Bean(name = "CLUSTER_MANAGER_COMMISSION_STATE")
    public Action<?, ?> cmCommissionAction() {
        return new AbstractClusterUpscaleVAltActions<>(UpscaleVAltStartInstancesResult.class) {

            @Override
            protected void doExecute(ClusterUpscaleVAltContext context, UpscaleVAltStartInstancesResult payload, Map<Object, Object> variables) throws Exception {
                List<String> instanceIds = payload.getStartedInstances().stream().map(x -> x.getInstanceId()).collect(Collectors.toList());
                clusterUpscaleFlowService.upscaleCommissionNewNodes(context.getStack().getId(), context.getHostGroupName(), instanceIds);

                // ZZZ: TODO This needs more looking into. What actually needs to be updated into the databases ?
//            Stack updatedStack = instanceMetaDataService.saveInstanceAndGetUpdatedStack(context.getStack(), instanceCountToCreate,
//                    context.getInstanceGroupName(), true, context.getHostNames(), context.isRepair());

                UpscaleClusterVAltCommissionViaCMRequest commissionRequest = new UpscaleClusterVAltCommissionViaCMRequest(context.getStack().getId(), context.getHostGroupName(), payload.getStartedInstances());
                sendEvent(context, commissionRequest);

            }
        };
    }



    @Bean(name = "FINALIZE_UPSCALE_VALT_STATE")
    public Action<?, ?> upscaleFinishedAction() {
        return new AbstractClusterUpscaleVAltActions<>(UpscaleClusterVAltCommissionViaCMResult.class) {

            @Override
            protected void doExecute(ClusterUpscaleVAltContext context, UpscaleClusterVAltCommissionViaCMResult payload, Map<Object, Object> variables) throws Exception {
                LOGGER.info("ZZZ: Marking upscale as finished.");

                clusterUpscaleFlowService.clusterUpscaleFinished(context.getStackView(), context.getHostGroupName());
                getMetricService().incrementMetricCounter(MetricType.CLUSTER_UPSCALE_SUCCESSFUL, context.getStack());
                sendEvent(context, FINALIZED_EVENT.event(), payload);
            }

            @Override
            protected Selectable createRequest(ClusterUpscaleVAltContext context) {
                return null;
            }
        };
    }


    @Bean(name = "UPSCALE_V_ALT_FAILED_STATE")
    public Action<?, ?> clusterUpscaleFailedAction() {
        return new AbstractStackFailureAction<ClusterUpscaleVAltState, ClusterUpscaleVAltEvents>() {

            @Override
            protected void doExecute(StackFailureContext context, StackFailureEvent payload, Map<Object, Object> variables) {
                clusterUpscaleFlowService.clusterUpscaleFailed(context.getStackView().getId(), payload.getException());
                getMetricService().incrementMetricCounter(MetricType.CLUSTER_UPSCALE_FAILED, context.getStackView(), payload.getException());
                ClusterUpscaleFailedConclusionRequest request = new ClusterUpscaleFailedConclusionRequest(context.getStackView().getId());
                sendEvent(context, request.selector(), request);
            }
        };
    }


    private abstract static class AbstractClusterUpscaleVAltActions<P extends Payload>
            extends AbstractStackAction<ClusterUpscaleVAltState, ClusterUpscaleVAltEvents, ClusterUpscaleVAltContext, P> {
        static final String HOSTGROUPNAME = "HOSTGROUPNAME";

        static final String ADJUSTMENT = "ADJUSTMENT";

        static final String SINGLE_PRIMARY_GATEWAY = "SINGLE_PRIMARY_GATEWAY";

        static final String HOST_NAME = "HOST_NAME";

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

        AbstractClusterUpscaleVAltActions(Class<P> payloadClass) {
            super(payloadClass);
        }

        @Override
        protected Object getFailurePayload(P payload, Optional<ClusterUpscaleVAltContext> flowContext, Exception ex) {
            return new StackFailureEvent(payload.getResourceId(), ex);
        }

        @Override
        protected ClusterUpscaleVAltContext createFlowContext(FlowParameters flowParameters, StateContext<ClusterUpscaleVAltState,
                ClusterUpscaleVAltEvents> stateContext, P payload) {
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

            return new ClusterUpscaleVAltContext(flowParameters, stack, stackService.getViewByIdWithoutAuth(stack.getId()), cloudContext, cloudCredential, cloudStack,
                    getHostgroupName(variables), getAdjustment(variables),
                    isSinglePrimaryGateway(variables), getPrimaryGatewayHostName(variables), getClusterManagerType(variables), isRestartServices(variables));
        }

        private String getHostgroupName(Map<Object, Object> variables) {
            return (String) variables.get(HOSTGROUPNAME);
        }

        private Integer getAdjustment(Map<Object, Object> variables) {
            return (Integer) variables.get(ADJUSTMENT);
        }

        private Boolean isSinglePrimaryGateway(Map<Object, Object> variables) {
            return (Boolean) variables.get(SINGLE_PRIMARY_GATEWAY);
        }

        private String getPrimaryGatewayHostName(Map<Object, Object> variables) {
            return (String) variables.get(HOST_NAME);
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
