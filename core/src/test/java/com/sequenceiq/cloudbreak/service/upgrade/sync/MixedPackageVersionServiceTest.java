package com.sequenceiq.cloudbreak.service.upgrade.sync;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.UPDATE_IN_PROGRESS;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.cloudbreak.cloud.model.ClouderaManagerProduct;
import com.sequenceiq.cloudbreak.cloud.model.Image;
import com.sequenceiq.cloudbreak.event.ResourceEvent;
import com.sequenceiq.cloudbreak.service.image.ClusterUpgradeTargetImageService;
import com.sequenceiq.cloudbreak.service.parcel.ClouderaManagerProductTransformer;
import com.sequenceiq.cloudbreak.service.upgrade.sync.common.ParcelInfo;
import com.sequenceiq.cloudbreak.service.upgrade.sync.operationresult.CmParcelSyncOperationResult;
import com.sequenceiq.cloudbreak.service.upgrade.sync.operationresult.CmRepoSyncOperationResult;
import com.sequenceiq.cloudbreak.service.upgrade.sync.operationresult.CmSyncOperationResult;
import com.sequenceiq.cloudbreak.structuredevent.event.CloudbreakEventService;

@ExtendWith(MockitoExtension.class)
public class MixedPackageVersionServiceTest {

    private static final long STACK_ID = 1L;

    private static final String CM_VERSION = "7.2.2";

    private static final String CDH_VERSION = "7.2.2";

    @InjectMocks
    private MixedPackageVersionService underTest;

    @Mock
    private CloudbreakEventService eventService;

    @Mock
    private ClusterUpgradeTargetImageService clusterUpgradeTargetImageService;

    @Mock
    private ClouderaManagerProductTransformer clouderaManagerProductTransformer;

    @Test
    void validateClusterPackageVersionsShouldNotSendNotificationWhenThePackageVersionsAreValidAndTheTargetImageIsPresent() {
        Map<String, String> parcelVersionsByName = Map.of("CDH", "7.2.2", "SPARK", "3.1.5");
        CmSyncOperationResult cmSyncOperationResult = createCmSyncResult(CM_VERSION, parcelVersionsByName);
        Image targetImage = createTargetImage(CM_VERSION, parcelVersionsByName);
        when(clusterUpgradeTargetImageService.findTargetImage(STACK_ID)).thenReturn(Optional.of(targetImage));

        underTest.validateClusterPackageVersions(STACK_ID, cmSyncOperationResult, Collections.emptySet());

        verify(clusterUpgradeTargetImageService).findTargetImage(STACK_ID);
        verifyNoInteractions(clouderaManagerProductTransformer);
        verifyNoInteractions(eventService);
    }

    @Test
    void validateClusterPackageVersionsShouldSendNotificationWhenTheCmVersionIsNotValidAndTheTargetImageIsPresent() {
        Map<String, String> parcelVersionsByName = Map.of("CDH", "7.2.2", "SPARK", "3.1.5");
        CmSyncOperationResult cmSyncOperationResult = createCmSyncResult("7.2.7", parcelVersionsByName);
        Image targetImage = createTargetImage(CM_VERSION, parcelVersionsByName);
        when(clusterUpgradeTargetImageService.findTargetImage(STACK_ID)).thenReturn(Optional.of(targetImage));

        underTest.validateClusterPackageVersions(STACK_ID, cmSyncOperationResult, Collections.emptySet());

        verify(clusterUpgradeTargetImageService).findTargetImage(STACK_ID);
        verify(eventService).fireCloudbreakEvent(STACK_ID, UPDATE_IN_PROGRESS.name(), ResourceEvent.STACK_CM_MIXED_PACKAGE_VERSIONS);
        verifyNoInteractions(clouderaManagerProductTransformer);
    }

    @Test
    void validateClusterPackageVersionsShouldSendNotificationWhenTheCDHVersionIsNotValidAndTheTargetImageIsPresent() {
        CmSyncOperationResult cmSyncOperationResult = createCmSyncResult(CM_VERSION, Map.of("CDH", "7.2.2", "SPARK", "3.1.5"));
        Image targetImage = createTargetImage(CM_VERSION, Map.of("CDH", "7.2.9", "SPARK", "3.1.5"));
        when(clusterUpgradeTargetImageService.findTargetImage(STACK_ID)).thenReturn(Optional.of(targetImage));

        underTest.validateClusterPackageVersions(STACK_ID, cmSyncOperationResult, Collections.emptySet());

        verify(clusterUpgradeTargetImageService).findTargetImage(STACK_ID);
        verify(eventService).fireCloudbreakEvent(STACK_ID, UPDATE_IN_PROGRESS.name(), ResourceEvent.STACK_CM_MIXED_PACKAGE_VERSIONS);
        verifyNoInteractions(clouderaManagerProductTransformer);
    }

    @Test
    void validateClusterPackageVersionsShouldNotSendNotificationWhenTheTargetImageIsNotPresent() {
        CmSyncOperationResult cmSyncOperationResult = createCmSyncResult(CM_VERSION, Map.of("CDH", CDH_VERSION, "SPARK", "3.1.5"));

        com.sequenceiq.cloudbreak.cloud.model.catalog.Image otherImage1 = createCatalogImage("image1", CM_VERSION);
        when(clouderaManagerProductTransformer.transform(otherImage1, true, true)).thenReturn(createCmProducts(Map.of("CDH", CDH_VERSION)));

        com.sequenceiq.cloudbreak.cloud.model.catalog.Image otherImage2 = createCatalogImage("image2", "7.2.7");

        com.sequenceiq.cloudbreak.cloud.model.catalog.Image otherImage3 = createCatalogImage("image3", CM_VERSION);
        when(clouderaManagerProductTransformer.transform(otherImage3, true, true)).thenReturn(createCmProducts(Map.of("CDH", "7.2.7", "SPARK", "3.1.5")));

        com.sequenceiq.cloudbreak.cloud.model.catalog.Image properImage = createCatalogImage("properImage", CM_VERSION);
        when(clouderaManagerProductTransformer.transform(properImage, true, true)).thenReturn(createCmProducts(Map.of("CDH", CDH_VERSION, "SPARK", "3.1.5")));

        Set<com.sequenceiq.cloudbreak.cloud.model.catalog.Image> candidateImages = new LinkedHashSet<>();
        candidateImages.add(otherImage1);
        candidateImages.add(otherImage2);
        candidateImages.add(otherImage3);
        candidateImages.add(properImage);

        when(clusterUpgradeTargetImageService.findTargetImage(STACK_ID)).thenReturn(Optional.empty());

        underTest.validateClusterPackageVersions(STACK_ID, cmSyncOperationResult, candidateImages);

        verify(clusterUpgradeTargetImageService).findTargetImage(STACK_ID);
        verify(clouderaManagerProductTransformer).transform(otherImage1, true, true);
        verify(clouderaManagerProductTransformer).transform(otherImage3, true, true);
        verify(clouderaManagerProductTransformer).transform(properImage, true, true);
        verifyNoMoreInteractions(clouderaManagerProductTransformer);
        verifyNoInteractions(eventService);
    }

    @Test
    void validateClusterPackageVersionsShouldSendNotificationWhenTheTargetImageIsNotPresent() {
        CmSyncOperationResult cmSyncOperationResult = createCmSyncResult(CM_VERSION, Map.of("CDH", CDH_VERSION, "SPARK", "3.1.5"));

        com.sequenceiq.cloudbreak.cloud.model.catalog.Image otherImage1 = createCatalogImage("image1", CM_VERSION);
        when(clouderaManagerProductTransformer.transform(otherImage1, true, true)).thenReturn(createCmProducts(Map.of("CDH", CDH_VERSION)));

        com.sequenceiq.cloudbreak.cloud.model.catalog.Image otherImage2 = createCatalogImage("image2", "7.2.7");

        com.sequenceiq.cloudbreak.cloud.model.catalog.Image otherImage3 = createCatalogImage("image3", CM_VERSION);
        when(clouderaManagerProductTransformer.transform(otherImage3, true, true)).thenReturn(createCmProducts(Map.of("CDH", "7.2.7", "SPARK", "3.1.5")));

        Set<com.sequenceiq.cloudbreak.cloud.model.catalog.Image> candidateImages = new LinkedHashSet<>();
        candidateImages.add(otherImage1);
        candidateImages.add(otherImage2);
        candidateImages.add(otherImage3);

        when(clusterUpgradeTargetImageService.findTargetImage(STACK_ID)).thenReturn(Optional.empty());

        underTest.validateClusterPackageVersions(STACK_ID, cmSyncOperationResult, candidateImages);

        verify(clusterUpgradeTargetImageService).findTargetImage(STACK_ID);
        verify(clouderaManagerProductTransformer).transform(otherImage1, true, true);
        verify(clouderaManagerProductTransformer).transform(otherImage3, true, true);
        verifyNoMoreInteractions(clouderaManagerProductTransformer);
        verify(eventService).fireCloudbreakEvent(STACK_ID, UPDATE_IN_PROGRESS.name(), ResourceEvent.STACK_CM_MIXED_PACKAGE_VERSIONS);
    }

    private Set<ClouderaManagerProduct> createCmProducts(Map<String, String> parcels) {
        return parcels.entrySet().stream()
                .map(entry -> new ClouderaManagerProduct().withName(entry.getKey()).withVersion(entry.getValue()))
                .collect(Collectors.toSet());
    }

    private com.sequenceiq.cloudbreak.cloud.model.catalog.Image createCatalogImage(String imageId, String cmVersion) {
        return new com.sequenceiq.cloudbreak.cloud.model.catalog.Image(null, null, null, null, imageId, null, null, null,
                null,
                null,
                Map.of("cm", cmVersion),
                null,
                Collections.emptyList(), null, false, null, null);
    }

    private Image createTargetImage(String cmVersion, Map<String, String> parcelVersionsByName) {
        Map<String, String> packageVersions = new HashMap<>();
        packageVersions.put("cm", cmVersion);
        packageVersions.putAll(parcelVersionsByName);
        return new Image(null, null, null, null, null, null, null, packageVersions);
    }

    private CmSyncOperationResult createCmSyncResult(String cmVersion, Map<String, String> parcelVersionsByName) {
        return new CmSyncOperationResult(new CmRepoSyncOperationResult(cmVersion, null), createCmParcelSyncResult(parcelVersionsByName));
    }

    private CmParcelSyncOperationResult createCmParcelSyncResult(Map<String, String> parcelVersionsByName) {
        return new CmParcelSyncOperationResult(parcelVersionsByName.entrySet().stream()
                .map(parcel -> new ParcelInfo(parcel.getKey(), parcel.getValue()))
                .collect(Collectors.toCollection(LinkedHashSet::new)),
                Collections.emptySet());
    }
}