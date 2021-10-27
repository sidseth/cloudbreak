package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.InstanceStatus;
import com.sequenceiq.cloudbreak.cloud.CloudConnector;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.handler.CloudPlatformEventHandler;
import com.sequenceiq.cloudbreak.cloud.init.CloudPlatformConnectors;
import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;
import com.sequenceiq.cloudbreak.cloud.model.CloudVmInstanceStatus;
import com.sequenceiq.cloudbreak.converter.spi.InstanceMetaDataToCloudInstanceConverter;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.logger.MDCUtils;
import com.sequenceiq.cloudbreak.perflogger.PerfLogger;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterVAltRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleVAltStartInstancesResult;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class UpscaleClusterVAltHandler implements CloudPlatformEventHandler<UpscaleClusterVAltRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpscaleClusterVAltHandler.class);

    @Inject
    private CloudPlatformConnectors cloudPlatformConnectors;

    @Inject
    private EventBus eventBus;

    @Inject
    private InstanceMetaDataToCloudInstanceConverter instanceMetaDataToCloudInstanceConverter;

    @Override
    public Class<UpscaleClusterVAltRequest> type() {
        return UpscaleClusterVAltRequest.class;
    }

    @Override
    public void accept(Event<UpscaleClusterVAltRequest> event) {
        UpscaleClusterVAltRequest request = event.getData();
        LOGGER.info("ZZZ: UpscaleClusterHandler for v-alt scaling path: {}", event.getData().getResourceId());

        PerfLogger.get().opBegin(MDCUtils.getPerfContextString(), "UpscaleStackHandlerVAlt.accept");

        CloudContext cloudContext = request.getCloudContext();
        try {
            CloudConnector<?> connector = cloudPlatformConnectors.get(cloudContext.getPlatformVariant());
            AuthenticatedContext ac = getAuthenticatedContext(request, cloudContext, connector);

            List<InstanceMetaData> instanceMetaData = request.getInstanceMetaDataForHg();
            // ZZZ TODO: This should ideally be randomized a bit, so that different isntances are started/stopped each time. That said, there is value in
            //  a reliable pattern. i.e. something along the lines of sort by hostname within this list.
            List<InstanceMetaData> instancesToStart = instanceMetaData.stream()
                    .filter(s -> s.getInstanceStatus() == InstanceStatus.STOPPED)
                    .limit(request.getNumInstancesToStart())
                    .collect(Collectors.toList());

            LOGGER.info("ZZZ: All instances in hostGroup: count={}, instances={}", instanceMetaData.size(), instanceMetaData);

            LOGGER.info("ZZZ: Instance identified as start candidates: count={}, instances={}", instancesToStart.size(), instancesToStart);
            if (instancesToStart.size() < request.getNumInstancesToStart()) {
                LOGGER.info("ZZZ: There are fewer instances available to start as compared to the request. Requested: {}, Available: {}", request.getNumInstancesToStart(), instancesToStart.size());
            }

            if (instancesToStart.size() > 0) {
                LOGGER.info("ZZZ: Attempting to start instances");
                List<CloudInstance> cloudInstancesToStart = instanceMetaDataToCloudInstanceConverter.convert(instancesToStart, request.getStack().getEnvironmentCrn(), request.getStack().getStackAuthentication());
                List<CloudVmInstanceStatus> instanceStatuses = connector.instances().start(ac, null, cloudInstancesToStart);
                LOGGER.info("ZZZ: Instance start attempt complete");
            } else {
                LOGGER.info("ZZZ: Did not find any instances to start");
            }

            // TODO : Tempoarilty assuming that all isntances started successfully. Not bothering to map back from the results vs the originally computed list.
            //  so instancesToStart is the metaData that can be sent to the next stage. Normally, this would need to be filtered and re-built for the next step.
            UpscaleVAltStartInstancesResult result = new UpscaleVAltStartInstancesResult(request.getResourceId(), instancesToStart);

            eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
        } catch (Exception e) {

        } finally {
            PerfLogger.get().opEnd__(MDCUtils.getPerfContextString(), "UpscaleStackHandlerVAlt.accept");
        }
    }

    private AuthenticatedContext getAuthenticatedContext(UpscaleClusterVAltRequest<UpscaleVAltStartInstancesResult> request, CloudContext cloudContext,
            CloudConnector<?> connector) {
        return connector.authentication().authenticate(cloudContext, request.getCloudCredential());
    }
}
