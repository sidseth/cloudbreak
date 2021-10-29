package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.UPDATE_IN_PROGRESS;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_SCALING_UP_CMHOSTS_STARTED;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.CLUSTER_SCALING_UP_CMHOSTS_STARTING;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.cluster.api.ClusterDecomissionService;
import com.sequenceiq.cloudbreak.cluster.api.ClusterSetupService;
import com.sequenceiq.cloudbreak.cluster.service.ClusterClientInitException;
import com.sequenceiq.cloudbreak.common.exception.NotFoundException;
import com.sequenceiq.cloudbreak.core.flow2.stack.CloudbreakFlowMessageService;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.Cluster;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostGroup;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.logger.MDCUtils;
import com.sequenceiq.cloudbreak.perflogger.PerfLogger;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.UpscaleClusterVAltCommissionViaCMRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.orchestration.UpscaleClusterVAltCommissionViaCMResult;
import com.sequenceiq.cloudbreak.service.cluster.ClusterApiConnectors;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.cloudbreak.service.stack.InstanceMetaDataService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.flow.reactor.api.handler.EventHandler;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class UpscaleClusterVAltCommissionHandler implements EventHandler<UpscaleClusterVAltCommissionViaCMRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpscaleClusterVAltCommissionHandler.class);

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
        return EventSelectorUtil.selector(UpscaleClusterVAltCommissionViaCMRequest.class);
    }

    @Override
    public void accept(Event<UpscaleClusterVAltCommissionViaCMRequest> event) {
        UpscaleClusterVAltCommissionViaCMRequest request = event.getData();
        LOGGER.info("ZZZ: UpscaleClusterVAltCommissionHandler for: {}, {}", event.getData().getResourceId(), event);
        LOGGER.info("ZZZ: InstancesToCommissionViaCM: {}", request.getInstancesToCommission());

        PerfLogger.get().opBegin(MDCUtils.getPerfContextString(), "UpscaleClusterVAltCommissionHandler.accept");
        try {
            Stack stack = request.getStack();
            Cluster cluster = stack.getCluster();

            flowMessageService.fireEventAndLog(stack.getId(), UPDATE_IN_PROGRESS.name(), CLUSTER_SCALING_UP_CMHOSTS_STARTING, String.valueOf(request.getInstancesToCommission().size()));
            PerfLogger.get().opBegin(MDCUtils.getPerfContextString(), "UpscaleClusterVAltCommissionHandler.waitForHosts.accept");
            ClusterSetupService clusterSetupService = clusterApiConnectors.getConnector(stack).clusterSetupService();
            clusterSetupService.waitForHosts2(new HashSet(request.getInstancesToCommission()));
            PerfLogger.get().opEnd__(MDCUtils.getPerfContextString(), "UpscaleClusterVAltCommissionHandler.waitForHosts.accept");
            flowMessageService.fireEventAndLog(stack.getId(), UPDATE_IN_PROGRESS.name(), CLUSTER_SCALING_UP_CMHOSTS_STARTED, String.valueOf(request.getInstancesToCommission().size()));

            ClusterDecomissionService clusterDecomissionService = clusterApiConnectors.getConnector(stack).clusterDecomissionService();

            // ZZZ No null fqdn etc checking in place. Rant:  Java Streams are terrible to easily get things wrong, and not think through what could potetntially braek. Not to mention the syntax..
            Set<String> hostNames = request.getInstancesToCommission().stream().map(x -> x.getDiscoveryFQDN()).collect(Collectors.toSet());
            LOGGER.info("ZZZ: hostNamesToRecommission: count={}, hostNames={}", hostNames.size(), hostNames);

            HostGroup hostGroup = hostGroupService.getByClusterIdAndName(cluster.getId(), request.getHostGroupName())
                    .orElseThrow(NotFoundException.notFound("hostgroup", request.getHostGroupName()));

            Map<String, InstanceMetaData> hostsToRecommission = clusterDecomissionService.collectHostsToRemove(hostGroup, hostNames);
            LOGGER.info("ZZZ: hostNamesToRecommission after checking with CM: count={}, details={}", hostsToRecommission.size(), hostsToRecommission);

            Set<String> recommissionedHostnames = Collections.emptySet();
            if (hostsToRecommission.size() > 0) {
                recommissionedHostnames = clusterDecomissionService.recommissionClusterNodes(hostsToRecommission);
            }
            LOGGER.info("ZZZ: hostsRecommissioned: count={}, hostNames={}", recommissionedHostnames.size(), recommissionedHostnames);

            // TODO ZZZ: Ideally wait for services to start.

            UpscaleClusterVAltCommissionViaCMResult result = new UpscaleClusterVAltCommissionViaCMResult(request);
            eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
        } catch (ClusterClientInitException e) {
            throw new RuntimeException(e);
        } finally {
            PerfLogger.get().opEnd__(MDCUtils.getPerfContextString(), "UpscaleClusterVAltCommissionHandler.accept");
        }
    }
}
