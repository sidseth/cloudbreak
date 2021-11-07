package com.sequenceiq.freeipa.service.stack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.cloudbreak.common.exception.BadRequestException;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.entity.StackStatus;
import com.sequenceiq.freeipa.service.freeipa.flow.FreeIpaFlowManager;

@ExtendWith(MockitoExtension.class)
class FreeIpaUpgradeCcmServiceTest {

    private static final String STACK_NAME = "stackName";

    private static final String ENVIRONMENT_CRN = "environmentCrn";

    private static final String ACCOUNT_ID = "accountId";

    @Mock
    private FreeIpaFlowManager flowManager;

    @Mock
    private StackService stackService;

    @Mock
    private StackUpdater stackUpdater;

    @InjectMocks
    private FreeIpaUpgradeCcmService underTest;

    @Test
    void upgradeCcmTestWhenAvailable() {
        Stack stack = createStack(Status.AVAILABLE);
        when(stackService.getByEnvironmentCrnAndAccountIdWithLists(ENVIRONMENT_CRN, ACCOUNT_ID)).thenReturn(stack);

        underTest.upgradeCcm(ENVIRONMENT_CRN, ACCOUNT_ID);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Status.class, names = {"AVAILABLE"}, mode = EnumSource.Mode.EXCLUDE)
    void upgradeCcmTestWhenNotAvailable(Status status) {
        Stack stack = createStack(status);
        when(stackService.getByEnvironmentCrnAndAccountIdWithLists(ENVIRONMENT_CRN, ACCOUNT_ID)).thenReturn(stack);

        BadRequestException badRequestException = assertThrows(BadRequestException.class, () -> underTest.upgradeCcm(ENVIRONMENT_CRN, ACCOUNT_ID));
        assertThat(badRequestException).hasMessage("FreeIPA stack 'stackName' must be AVAILABLE to start Cluster Connectivity Manager upgrade.");
    }

    private Stack createStack(Status status) {
        Stack stack = new Stack();
        stack.setName(STACK_NAME);
        StackStatus stackStatus = new StackStatus();
        stack.setStackStatus(stackStatus);
        stackStatus.setStatus(status);
        return stack;
    }

}