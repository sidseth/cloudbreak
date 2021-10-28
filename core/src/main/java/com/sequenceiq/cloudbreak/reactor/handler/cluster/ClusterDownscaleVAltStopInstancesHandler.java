package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterDownscaleVAltStopInstancesRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.ClusterDownscaleVAltStopInstancesResult;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class ClusterDownscaleVAltStopInstancesHandler implements CloudPlatformEventHandler<ClusterDownscaleVAltStopInstancesRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterDownscaleVAltStopInstancesHandler.class);

    @Inject
    private CloudPlatformConnectors cloudPlatformConnectors;

    @Inject
    private EventBus eventBus;

    @Inject
    private InstanceMetaDataToCloudInstanceConverter instanceMetaDataToCloudInstanceConverter;


    @Override
    public Class<ClusterDownscaleVAltStopInstancesRequest> type() {
        return ClusterDownscaleVAltStopInstancesRequest.class;
    }

    @Override
    public void accept(Event<ClusterDownscaleVAltStopInstancesRequest> event) {
        ClusterDownscaleVAltStopInstancesRequest request = event.getData();
        LOGGER.info("ZZZ: ClusterDownscaleVAltStopInstancesHandler for v-alt scaling path: {}", request);

        PerfLogger.get().opBegin(MDCUtils.getPerfContextString(), "ClusterDownscaleVAltStopInstancesHandler.accept");

        CloudContext cloudContext = request.getCloudContext();
        try {
            // Get the nodeIds in the required format.
            // Send out a request to the cloud provider to stop the instances.
            // Wait for the instances to stop.

            CloudConnector<?> connector = cloudPlatformConnectors.get(cloudContext.getPlatformVariant());
            AuthenticatedContext ac = getAuthenticatedContext(request, cloudContext, connector);

            List<InstanceMetaData> instanceMetaDataList = request.getInstanceMetaDataForEntireHg();
            LOGGER.info("ZZZ: All instances in hostGroup: count={}, instances={}", instanceMetaDataList.size(), instanceMetaDataList);

            Set<Long> instanceIdsToStop = request.getInstanceIdsToStop();
            LOGGER.info("ZZZ: InstanceIdsToStop: counte={}, ids={}", instanceIdsToStop.size(), instanceIdsToStop);

            List<InstanceMetaData> toStopInstanceMetaData = new LinkedList<>();

            for (InstanceMetaData instance : instanceMetaDataList) {
                if (instanceIdsToStop.contains(instance.getPrivateId())) {
                    toStopInstanceMetaData.add(instance);
                }
            }
            LOGGER.info("ZZZ: ToStopInstanceMetadata: count={}, md={}", toStopInstanceMetaData.size(), toStopInstanceMetaData);

            List<CloudInstance> cloudInstancesToStop = Collections.emptyList();
            List<CloudVmInstanceStatus> cloudVmInstanceStatusList = Collections.emptyList();
            if (instanceIdsToStop.size() > 0) {
                LOGGER.info("ZZZ: Attempting to stop instances");
                cloudInstancesToStop = instanceMetaDataToCloudInstanceConverter.convert(toStopInstanceMetaData, request.getStack().getEnvironmentCrn(), request.getStack().getStackAuthentication());
                cloudVmInstanceStatusList = connector.instances().stop(ac, null, cloudInstancesToStop);
                LOGGER.info("ZZZ: CloudVMInstanceStatusesPostStop={}", cloudVmInstanceStatusList);
            } else {
                LOGGER.info("ZZZ: Did not find any instances to stop");
            }

            ClusterDownscaleVAltStopInstancesResult result = new ClusterDownscaleVAltStopInstancesResult(request.getResourceId(), cloudInstancesToStop, cloudVmInstanceStatusList, toStopInstanceMetaData);
            eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
        } catch (Exception e) {
            // ZZZ: Do something useful here
            throw e;
        } finally {
            PerfLogger.get().opEnd__(MDCUtils.getPerfContextString(), "ClusterDownscaleVAltStopInstancesHandler.accept");
        }
    }

    private AuthenticatedContext getAuthenticatedContext(ClusterDownscaleVAltStopInstancesRequest<ClusterDownscaleVAltStopInstancesResult> request, CloudContext cloudContext,
            CloudConnector<?> connector) {
        return connector.authentication().authenticate(cloudContext, request.getCloudCredential());
    }
}
