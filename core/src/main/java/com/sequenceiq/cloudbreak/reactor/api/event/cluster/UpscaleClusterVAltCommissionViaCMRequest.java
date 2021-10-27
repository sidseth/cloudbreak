package com.sequenceiq.cloudbreak.reactor.api.event.cluster;

import java.util.List;

import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.AbstractClusterScaleRequest;

public class UpscaleClusterVAltCommissionViaCMRequest extends AbstractClusterScaleRequest {

    // ZZZ: TODO: Introduce the set of hosts which were successfully started - to be sent to the next step.

    private List<InstanceMetaData> instancesToCommission;

    public UpscaleClusterVAltCommissionViaCMRequest(Long stackId, String hostGroupName, List<InstanceMetaData> instanceList) {
        super(stackId, hostGroupName);
        this.instancesToCommission = instanceList;
    }

    public List<InstanceMetaData> getInstancesToCommission() {
        return instancesToCommission;
    }

    @Override
    public String toString() {
        return "UpscaleClusterVAltCommissionViaCMRequest{" +
                "instancesToCommission=" + instancesToCommission +
                '}';
    }
}
