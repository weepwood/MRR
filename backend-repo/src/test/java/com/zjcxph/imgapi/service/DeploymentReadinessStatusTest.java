package com.zjcxph.imgapi.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentReadinessStatusTest {

    @Test
    void anyNonServerErrorResponseMeansTheServerIsReachable() {
        assertTrue(DeploymentReadinessService.isServerReachableStatus(200));
        assertTrue(DeploymentReadinessService.isServerReachableStatus(302));
        assertTrue(DeploymentReadinessService.isServerReachableStatus(401));
        assertTrue(DeploymentReadinessService.isServerReachableStatus(403));
        assertTrue(DeploymentReadinessService.isServerReachableStatus(404));
        assertTrue(DeploymentReadinessService.isServerReachableStatus(405));
    }

    @Test
    void serverErrorsAndInvalidStatusesAreNotHealthyResponses() {
        assertFalse(DeploymentReadinessService.isServerReachableStatus(0));
        assertFalse(DeploymentReadinessService.isServerReachableStatus(500));
        assertFalse(DeploymentReadinessService.isServerReachableStatus(503));
    }
}
