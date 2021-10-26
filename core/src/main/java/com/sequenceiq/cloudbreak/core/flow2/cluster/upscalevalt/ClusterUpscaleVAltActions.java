package com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt;


import static com.sequenceiq.cloudbreak.core.flow2.cluster.upscalevalt.ClusterUpscaleVAltEvents.FINALIZED_EVENT;

import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

import com.sequenceiq.cloudbreak.common.event.Payload;
import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.cloudbreak.common.type.ClusterManagerType;
import com.sequenceiq.cloudbreak.core.flow2.AbstractStackAction;
import com.sequenceiq.cloudbreak.core.flow2.event.ClusterScaleVAltTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.stack.AbstractStackFailureAction;
import com.sequenceiq.cloudbreak.core.flow2.stack.StackFailureContext;
import com.sequenceiq.cloudbreak.domain.view.StackView;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.reactor.api.event.StackFailureEvent;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterUpscaleFailedConclusionRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterVAltCommissionViaCMRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleClusterVAltCommissionViaCMResult;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleVAltStartInstancesResult;
import com.sequenceiq.cloudbreak.service.metrics.MetricType;
import com.sequenceiq.cloudbreak.service.stack.StackService;
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
                clusterUpscaleFlowService.upscalingClusterManager(context.getStackId(), payload.getHostGroupName());
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(ClusterUpscaleVAltContext context) {
                return new UpscaleClusterRequest(context.getStackId(), context.getHostGroupName(), false, false);
            }
        };
    }

    @Bean(name = "CLUSTER_MANAGER_COMMISSION_STATE")
    public Action<?, ?> cmCommissionAction() {
        return new AbstractClusterUpscaleVAltActions<>(UpscaleVAltStartInstancesResult.class) {

            @Override
            protected void doExecute(ClusterUpscaleVAltContext context, UpscaleVAltStartInstancesResult payload, Map<Object, Object> variables) throws Exception {
                clusterUpscaleFlowService.upscaleCommissionNewNodes(context.getStackId(), context.getHostGroupName());
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(ClusterUpscaleVAltContext context) {
                return new UpscaleClusterVAltCommissionViaCMRequest(context.getStackId(), context.getHostGroupName());
            }
        };
    }



    @Bean(name = "FINALIZE_UPSCALE_VALT_STATE")
    public Action<?, ?> upscaleFinishedAction() {
        return new AbstractClusterUpscaleVAltActions<>(UpscaleClusterVAltCommissionViaCMResult.class) {

            @Override
            protected void doExecute(ClusterUpscaleVAltContext context, UpscaleClusterVAltCommissionViaCMResult payload, Map<Object, Object> variables) throws Exception {
                clusterUpscaleFlowService.clusterUpscaleFinished(context.getStack(), context.getHostGroupName());
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
            StackView stack = stackService.getViewByIdWithoutAuth(payload.getResourceId());
            MDCBuilder.buildMdcContext(stack.getClusterView());
            return new ClusterUpscaleVAltContext(flowParameters, stack, getHostgroupName(variables), getAdjustment(variables), isSinglePrimaryGateway(variables),
                    getPrimaryGatewayHostName(variables), getClusterManagerType(variables), isRestartServices(variables));
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
