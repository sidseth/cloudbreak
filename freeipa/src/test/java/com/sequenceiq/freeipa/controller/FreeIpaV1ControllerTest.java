package com.sequenceiq.freeipa.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.authorization.resource.AuthorizationResourceAction;
import com.sequenceiq.cloudbreak.common.exception.BadRequestException;
import com.sequenceiq.cloudbreak.validation.ValidationResult;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.FreeIpaServerBase;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.attachchildenv.AttachChildEnvironmentRequest;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.create.CreateFreeIpaRequest;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.detachchildenv.DetachChildEnvironmentRequest;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.list.ListFreeIpaResponse;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.reboot.RebootInstancesRequest;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.repair.RepairInstancesRequest;
import com.sequenceiq.freeipa.authorization.FreeIpaFiltering;
import com.sequenceiq.freeipa.client.FreeIpaClientException;
import com.sequenceiq.freeipa.controller.validation.AttachChildEnvironmentRequestValidator;
import com.sequenceiq.freeipa.controller.validation.CreateFreeIpaRequestValidator;
import com.sequenceiq.freeipa.service.freeipa.cert.root.FreeIpaRootCertificateService;
import com.sequenceiq.freeipa.service.image.ImageCatalogGeneratorService;
import com.sequenceiq.freeipa.service.stack.ChildEnvironmentService;
import com.sequenceiq.freeipa.service.stack.FreeIpaCreationService;
import com.sequenceiq.freeipa.service.stack.FreeIpaDeletionService;
import com.sequenceiq.freeipa.service.stack.FreeIpaDescribeService;
import com.sequenceiq.freeipa.service.stack.FreeIpaListService;
import com.sequenceiq.freeipa.service.stack.RepairInstancesService;
import com.sequenceiq.freeipa.util.CrnService;

@ExtendWith(MockitoExtension.class)
class FreeIpaV1ControllerTest {

    private static final String ENVIRONMENT_CRN = "test:environment:crn";

    private static final String ACCOUNT_ID = "accountId";

    @InjectMocks
    private FreeIpaV1Controller underTest;

    @Mock
    private FreeIpaDeletionService deletionService;

    @Mock
    private FreeIpaCreationService creationService;

    @Mock
    private ChildEnvironmentService childEnvironmentService;

    @Mock
    private FreeIpaDescribeService describeService;

    @Mock
    private FreeIpaListService freeIpaListService;

    @Mock
    private FreeIpaRootCertificateService rootCertificateService;

    @Mock
    private CrnService crnService;

    @Mock
    private CreateFreeIpaRequestValidator createFreeIpaRequestValidatior;

    @Mock
    private AttachChildEnvironmentRequestValidator attachChildEnvironmentRequestValidator;

    @Mock
    private RepairInstancesService repairInstancesService;

    @Mock
    private FreeIpaFiltering freeIpaFiltering;

    @Mock
    private ImageCatalogGeneratorService imageCatalogGeneratorService;

    @BeforeEach
    void setUp() {
        lenient().when(crnService.getCurrentAccountId()).thenReturn(ACCOUNT_ID);
    }

    @Test
    void create() {
        CreateFreeIpaRequest freeIpaRequest = new CreateFreeIpaRequest();
        when(createFreeIpaRequestValidatior.validate(freeIpaRequest)).thenReturn(ValidationResult.builder().build());
        assertNull(underTest.create(freeIpaRequest));

        verify(creationService, times(1)).launchFreeIpa(freeIpaRequest, ACCOUNT_ID);
    }

    @Test
    void attachChildEnvironment() {
        AttachChildEnvironmentRequest attachChildEnvironmentRequest = new AttachChildEnvironmentRequest();
        when(attachChildEnvironmentRequestValidator.validate(attachChildEnvironmentRequest)).thenReturn(ValidationResult.builder().build());

        underTest.attachChildEnvironment(attachChildEnvironmentRequest);

        verify(childEnvironmentService, times(1)).attachChildEnvironment(attachChildEnvironmentRequest, ACCOUNT_ID);
    }

    @Test
    void createValidationError() {
        CreateFreeIpaRequest freeIpaRequest = new CreateFreeIpaRequest();
        when(createFreeIpaRequestValidatior.validate(freeIpaRequest)).thenReturn(ValidationResult.builder().error("error").build());
        BadRequestException badRequestException = assertThrows(BadRequestException.class, () -> underTest.create(freeIpaRequest));
        assertEquals("error", badRequestException.getMessage());
        verify(creationService, never()).launchFreeIpa(freeIpaRequest, ACCOUNT_ID);
    }

    @Test
    void createRequestDomainPattern() {
        final Pattern domainPattern = Pattern.compile(FreeIpaServerBase.DOMAIN_MATCHER);
        Map<String, Boolean> domainTestSequences = Map.of(
                "domain", Boolean.FALSE,
                ".domain", Boolean.FALSE,
                "local.domain", Boolean.TRUE,
                "123.domain", Boolean.TRUE,
                "local-123.domain", Boolean.TRUE,
                "local-123.domain.com", Boolean.TRUE,
                "local.domain?", Boolean.FALSE
        );
        domainTestSequences.forEach((domain, expectation) -> {
            Matcher domainMatcher = domainPattern.matcher(domain);
            assertEquals(expectation, domainMatcher.matches(), String.format("testing %s", domain));
        });
    }

    @Test
    void createRequestHostnamePattern() {
        final Pattern hostnamePattern = Pattern.compile(FreeIpaServerBase.HOSTNAME_MATCHER);
        Map<String, Boolean> hostnameTestSequences = Map.of(
                "hostname", Boolean.TRUE,
                "123-hostname", Boolean.TRUE,
                ".hostname", Boolean.FALSE,
                "local.hostname", Boolean.FALSE,
                "123.hostname", Boolean.FALSE,
                "local.hostname?", Boolean.FALSE
        );
        hostnameTestSequences.forEach((hostname, expectation) -> {
            Matcher hostnameMatcher = hostnamePattern.matcher(hostname);
            assertEquals(expectation, hostnameMatcher.matches(), String.format("testing %s", hostname));
        });
    }

    @Test
    void describe() {
        assertNull(underTest.describe(ENVIRONMENT_CRN));
    }

    @Test
    void list() {
        List<ListFreeIpaResponse> responseList = Collections.singletonList(new ListFreeIpaResponse());
        when(freeIpaFiltering.filterFreeIpas(AuthorizationResourceAction.DESCRIBE_ENVIRONMENT)).thenReturn(responseList);

        List<ListFreeIpaResponse> actual = underTest.list();

        assertEquals(responseList, actual);
    }

    @Test
    void listInternal() {
        List<ListFreeIpaResponse> responseList = Collections.singletonList(new ListFreeIpaResponse());
        when(freeIpaListService.list(ACCOUNT_ID)).thenReturn(responseList);

        List<ListFreeIpaResponse> actual = underTest.listInternal(ACCOUNT_ID);

        assertEquals(responseList, actual);
        verify(freeIpaListService).list(ACCOUNT_ID);
    }

    @Test
    void getRootCertificate() throws Exception {
        underTest.getRootCertificate(ENVIRONMENT_CRN);
        verify(rootCertificateService, times(1)).getRootCertificate(ENVIRONMENT_CRN, ACCOUNT_ID);
    }

    @Test
    void delete() {
        underTest.delete(ENVIRONMENT_CRN, true);

        verify(deletionService, times(1)).delete(ENVIRONMENT_CRN, ACCOUNT_ID, true);
    }

    @Test
    void detachChildEnvironment() {
        DetachChildEnvironmentRequest request = mock(DetachChildEnvironmentRequest.class);

        when(crnService.getCurrentAccountId()).thenReturn(ACCOUNT_ID);

        underTest.detachChildEnvironment(request);

        verify(childEnvironmentService).detachChildEnvironment(request, ACCOUNT_ID);
    }

    @Test
    void reboot() throws FreeIpaClientException {
        when(crnService.getCurrentAccountId()).thenReturn(ACCOUNT_ID);
        RebootInstancesRequest rebootInstancesRequest = new RebootInstancesRequest();

        underTest.rebootInstances(rebootInstancesRequest);
        verify(repairInstancesService, times(1)).rebootInstances(crnService.getCurrentAccountId(), rebootInstancesRequest);
    }

    @Test
    void repair() throws FreeIpaClientException {
        when(crnService.getCurrentAccountId()).thenReturn(ACCOUNT_ID);
        RepairInstancesRequest request = new RepairInstancesRequest();

        underTest.repairInstances(request);
        verify(repairInstancesService, times(1)).repairInstances(crnService.getCurrentAccountId(), request);
    }

    @Test
    void generateImageCatalog() {
        when(crnService.getCurrentAccountId()).thenReturn(ACCOUNT_ID);

        underTest.generateImageCatalog(ENVIRONMENT_CRN);

        verify(imageCatalogGeneratorService).generate(ENVIRONMENT_CRN, ACCOUNT_ID);
    }
}
