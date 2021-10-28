package com.sequenceiq.cloudbreak.service.upgrade.sync;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.UPDATE_IN_PROGRESS;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.cloud.model.ClouderaManagerProduct;
import com.sequenceiq.cloudbreak.cloud.model.Image;
import com.sequenceiq.cloudbreak.common.exception.CloudbreakServiceException;
import com.sequenceiq.cloudbreak.event.ResourceEvent;
import com.sequenceiq.cloudbreak.service.image.ClusterUpgradeTargetImageService;
import com.sequenceiq.cloudbreak.service.parcel.ClouderaManagerProductTransformer;
import com.sequenceiq.cloudbreak.service.upgrade.sync.common.ParcelInfo;
import com.sequenceiq.cloudbreak.service.upgrade.sync.operationresult.CmSyncOperationResult;
import com.sequenceiq.cloudbreak.structuredevent.event.CloudbreakEventService;

@Service
public class MixedPackageVersionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MixedPackageVersionService.class);

    @Inject
    private CloudbreakEventService eventService;

    @Inject
    private ClusterUpgradeTargetImageService clusterUpgradeTargetImageService;

    @Inject
    private ClouderaManagerProductTransformer clouderaManagerProductTransformer;

    void validateClusterPackageVersions(Long stackId, CmSyncOperationResult cmSyncOperationResult,
            Set<com.sequenceiq.cloudbreak.cloud.model.catalog.Image> candidateImages) {
        Optional<Image> targetImage = clusterUpgradeTargetImageService.findTargetImage(stackId);
        String cmVersion = getCmVersion(cmSyncOperationResult);
        Set<ParcelInfo> installedParcels = cmSyncOperationResult.getCmParcelSyncOperationResult().getActiveParcels();
        targetImage.ifPresentOrElse(image -> examinePackageVersionsWithTargetImage(stackId, image, cmVersion, installedParcels),
                () -> examinePackageVersionsWithAllCandidateImage(stackId, candidateImages, cmVersion, installedParcels));
    }

    private String getCmVersion(CmSyncOperationResult cmSyncOperationResult) {
        return cmSyncOperationResult.getCmRepoSyncOperationResult().getInstalledCmVersion()
                .orElseThrow(() -> new CloudbreakServiceException("Cloudera Manager version is not found in the cluster."));
    }

    private void examinePackageVersionsWithTargetImage(Long stackId, Image targetImage, String cmVersion, Set<ParcelInfo> installedParcels) {
        LOGGER.debug("Comparing cluster package versions with target image {}", targetImage);
        boolean packageVersionsMatches = isCmRepoVersionMatches(cmVersion, targetImage.getPackageVersions())
                && allInstalledParcelVersionMatches(installedParcels, targetImage);
        examineResult(packageVersionsMatches, stackId);
    }

    private boolean allInstalledParcelVersionMatches(Set<ParcelInfo> installedParcels, Image targetImage) {
        return installedParcels.stream()
                .allMatch(parcelInfo -> targetImage.getPackageVersions().entrySet().stream()
                        .anyMatch(packages -> packages.getKey().equals(parcelInfo.getName()) && packages.getValue().equals(parcelInfo.getVersion())));
    }

    private void examinePackageVersionsWithAllCandidateImage(Long stackId, Set<com.sequenceiq.cloudbreak.cloud.model.catalog.Image> candidateImages,
            String cmVersion, Set<ParcelInfo> installedParcels) {
        LOGGER.debug("Target image not found for cluster therefore comparing package versions with other candidate images.");
        boolean packageVersionsMatches = allInstalledParcelVersionMatches(installedParcels, cmVersion, candidateImages);
        examineResult(packageVersionsMatches, stackId);

    }

    private boolean allInstalledParcelVersionMatches(Set<ParcelInfo> installedParcels, String cmVersion,
            Set<com.sequenceiq.cloudbreak.cloud.model.catalog.Image> candidateImages) {
        return candidateImages.stream().anyMatch(image -> isCmRepoVersionMatches(cmVersion, image.getPackageVersions()) &&
                matchParcelVersions(installedParcels, image));
    }

    private boolean isCmRepoVersionMatches(String cmVersion, Map<String, String> packageVersions) {
        return cmVersion.equals(packageVersions.get("cm"));
    }

    private boolean matchParcelVersions(Set<ParcelInfo> installedParcels, com.sequenceiq.cloudbreak.cloud.model.catalog.Image image) {
        Set<ClouderaManagerProduct> clouderaManagerProducts = clouderaManagerProductTransformer.transform(image, true, true);
        return installedParcels.stream()
                .allMatch(parcelInfo -> clouderaManagerProducts.stream()
                        .anyMatch(cmProduct -> cmProduct.getName().equals(parcelInfo.getName()) && cmProduct.getVersion().equals(parcelInfo.getVersion())));
    }

    private void examineResult(boolean packageVersionsMatches, Long stackId) {
        if (packageVersionsMatches) {
            LOGGER.debug("The combination of the cluster package versions are appropriate.");
        } else {
            sendWarningNotification(stackId);
        }
    }

    private void sendWarningNotification(Long stackId) {
        eventService.fireCloudbreakEvent(stackId, UPDATE_IN_PROGRESS.name(), ResourceEvent.STACK_CM_MIXED_PACKAGE_VERSIONS);
    }
}
