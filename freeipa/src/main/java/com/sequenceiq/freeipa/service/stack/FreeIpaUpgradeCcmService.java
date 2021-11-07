package com.sequenceiq.freeipa.service.stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.common.exception.BadRequestException;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.service.freeipa.flow.FreeIpaFlowManager;

@Service
public class FreeIpaUpgradeCcmService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreeIpaUpgradeCcmService.class);

    private final FreeIpaFlowManager flowManager;

    private final StackService stackService;

    private final StackUpdater stackUpdater;

    public FreeIpaUpgradeCcmService(FreeIpaFlowManager flowManager, StackService stackService, StackUpdater stackUpdater) {
        this.flowManager = flowManager;
        this.stackService = stackService;
        this.stackUpdater = stackUpdater;
    }

    public void upgradeCcm(String environmentCrn, String accountId) {
        MDCBuilder.addEnvCrn(environmentCrn);
        MDCBuilder.addAccountId(accountId);
        Stack stack = stackService.getByEnvironmentCrnAndAccountIdWithLists(environmentCrn, accountId);
        triggerStackCcmUpgradeFlow(stack);
    }

    private void triggerStackCcmUpgradeFlow(Stack stack) {
        MDCBuilder.buildMdcContext(stack);
        validateCcmUpgrade(stack);
        // TODO log & update stack status (if needed), then send event; see CB-14571
    }

    private void validateCcmUpgrade(Stack stack) {
        if (!stack.isAvailable()) {
            throw new BadRequestException(
                String.format("FreeIPA stack '%s' must be AVAILABLE to start Cluster Connectivity Manager upgrade.", stack.getName()));
        }
    }

}
