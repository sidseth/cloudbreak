package com.sequenceiq.cloudbreak.orchestrator.salt.poller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.sequenceiq.cloudbreak.orchestrator.exception.CloudbreakOrchestratorFailedException;
import com.sequenceiq.cloudbreak.orchestrator.exception.CloudbreakOrchestratorInProgressException;
import com.sequenceiq.cloudbreak.orchestrator.exception.CloudbreakOrchestratorTerminateException;
import com.sequenceiq.cloudbreak.orchestrator.salt.SaltErrorResolver;
import com.sequenceiq.cloudbreak.orchestrator.salt.client.SaltConnector;
import com.sequenceiq.cloudbreak.orchestrator.salt.client.target.Target;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.JobId;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.JobState;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.RunningJobsResponse;
import com.sequenceiq.cloudbreak.orchestrator.salt.states.SaltStates;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SaltStates.class)
public class SaltJobIdTrackerTest {

    @Captor
    private ArgumentCaptor<Target<String>> targetCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    @Test
    public void callWithNotStarted() throws Exception {
        String jobId = "1";
        SaltConnector saltConnector = Mockito.mock(SaltConnector.class);
        SaltJobRunner saltJobRunner = Mockito.mock(SaltJobRunner.class);
        PowerMockito.mockStatic(SaltStates.class);
        PowerMockito.when(SaltStates.jobIsRunning(any(), any())).thenReturn(true);
        RunningJobsResponse jobsResponse = new RunningJobsResponse();
        jobsResponse.setResult(List.of());
        PowerMockito.when(SaltStates.getRunningJobs(saltConnector)).thenReturn(jobsResponse);

        Set<String> targets = new HashSet<>();
        targets.add("10.0.0.1");
        targets.add("10.0.0.2");
        targets.add("10.0.0.3");
        when(saltJobRunner.getTargetHostnames()).thenReturn(targets);
        when(saltJobRunner.getJid()).thenReturn(JobId.jobId(jobId));
        when(saltJobRunner.getJobState()).thenReturn(JobState.NOT_STARTED, JobState.IN_PROGRESS);
        when(saltJobRunner.submit(any(SaltConnector.class))).thenReturn(jobId);
        SaltJobIdTracker saltJobIdTracker = new SaltJobIdTracker(saltConnector, saltJobRunner);
        try {
            saltJobIdTracker.call();
            fail("should throw exception");
        } catch (CloudbreakOrchestratorInProgressException e) {
            assertThat(e.getMessage(), allOf(containsString("Target:"), containsString("10.0.0.1"),
                    containsString("10.0.0.2"), containsString("10.0.0.3")));
        }
        PowerMockito.verifyStatic(SaltStates.class);
        SaltStates.jobIsRunning(any(), eq(jobId));
        checkTargets(targets, targetCaptor.getAllValues());
        verify(saltJobRunner, times(2)).getJobState();
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    @Test
    public void callWithNotStartedWithAlreadyRunning() throws Exception {
        String jobId = "1";
        SaltConnector saltConnector = Mockito.mock(SaltConnector.class);
        SaltJobRunner saltJobRunner = Mockito.mock(SaltJobRunner.class);
        PowerMockito.mockStatic(SaltStates.class);
        PowerMockito.when(SaltStates.jobIsRunning(any(), any())).thenReturn(true);
        RunningJobsResponse jobsResponse = new RunningJobsResponse();
        jobsResponse.setResult(List.of(Map.of("runningJob", Map.of())));
        PowerMockito.when(SaltStates.getRunningJobs(saltConnector)).thenReturn(jobsResponse);

        Set<String> targets = new HashSet<>();
        targets.add("10.0.0.1");
        targets.add("10.0.0.2");
        targets.add("10.0.0.3");
        when(saltJobRunner.getTargetHostnames()).thenReturn(targets);
        when(saltJobRunner.getJid()).thenReturn(JobId.jobId(jobId));
        when(saltJobRunner.getJobState()).thenReturn(JobState.NOT_STARTED);
        when(saltJobRunner.submit(any(SaltConnector.class))).thenReturn(jobId);
        SaltJobIdTracker saltJobIdTracker = new SaltJobIdTracker(saltConnector, saltJobRunner);
        try {
            saltJobIdTracker.call();
            fail("should throw exception");
        } catch (CloudbreakOrchestratorInProgressException e) {
            assertThat(e.getMessage(), allOf(containsString("There are running job(s) with id:"), containsString("runningJob")));
        }

        verify(saltJobRunner, times(1)).getJobState();
    }

    @Test
    public void callWithFailed() throws Exception {
        String jobId = "1";
        SaltConnector saltConnector = Mockito.mock(SaltConnector.class);
        PowerMockito.mockStatic(SaltStates.class);
        PowerMockito.when(SaltStates.jobIsRunning(any(), any())).thenReturn(true);

        SaltJobRunner saltJobRunner = Mockito.mock(BaseSaltJobRunner.class);
        when(saltJobRunner.getJid()).thenReturn(JobId.jobId(jobId));
        when(saltJobRunner.getJobState()).thenCallRealMethod();
        doCallRealMethod().when(saltJobRunner).setJobState(any());

        Set<String> targets = new HashSet<>();
        targets.add("10.0.0.1");
        targets.add("10.0.0.3");
        when(saltJobRunner.getTargetHostnames()).thenReturn(targets);
        when(saltJobRunner.submit(any(SaltConnector.class))).thenReturn(jobId);
        saltJobRunner.setJobState(JobState.FAILED);
        Multimap<String, String> multimap = ArrayListMultimap.create();
        multimap.put("10.0.0.1", "some error");
        when(saltJobRunner.getNodesWithError()).thenReturn(multimap);

        SaltJobIdTracker saltJobIdTracker = new SaltJobIdTracker(saltConnector, saltJobRunner, false);
        try {
            saltJobIdTracker.call();
            fail("should throw exception");
        } catch (CloudbreakOrchestratorTerminateException e) {
            assertThat(e.getMessage(), allOf(containsString("Target:"), containsString("10.0.0.1"), containsString("10.0.0.3"),
                    containsString("Node: 10.0.0.1 Error(s): some error")));
        }
    }

    private void checkTargets(Set<String> targets, List<Target<String>> allValues) {
        for (Target<String> allValue : allValues) {
            for (String target : targets) {
                assertThat(allValue.getTarget(), containsString(target));
            }
        }
    }

    @Test
    public void callWithInProgressAndJobIsRunning() throws Exception {
        String jobId = "1";
        SaltConnector saltConnector = Mockito.mock(SaltConnector.class);

        SaltJobRunner saltJobRunner = Mockito.mock(BaseSaltJobRunner.class);
        when(saltJobRunner.getJid()).thenReturn(JobId.jobId(jobId));
        when(saltJobRunner.getJobState()).thenCallRealMethod();
        doCallRealMethod().when(saltJobRunner).setJobState(any());

        when(saltJobRunner.submit(any(SaltConnector.class))).thenReturn(jobId);
        saltJobRunner.setJobState(JobState.IN_PROGRESS);

        Set<String> targets = new HashSet<>();
        targets.add("10.0.0.1");
        targets.add("10.0.0.2");
        targets.add("10.0.0.3");
        when(saltJobRunner.getTargetHostnames()).thenReturn(targets);

        PowerMockito.mockStatic(SaltStates.class);
        PowerMockito.when(SaltStates.jobIsRunning(any(), any())).thenReturn(true);

        SaltJobIdTracker saltJobIdTracker = new SaltJobIdTracker(saltConnector, saltJobRunner);
        try {
            saltJobIdTracker.call();
        } catch (CloudbreakOrchestratorInProgressException e) {
            assertThat(e.getMessage(), allOf(containsString("Target:"), containsString("10.0.0.1"), containsString("10.0.0.2"),
                    containsString("10.0.0.3")));
        }

        PowerMockito.verifyStatic(SaltStates.class);
        SaltStates.jobIsRunning(any(), eq(jobId));
        checkTargets(targets, targetCaptor.getAllValues());
    }

    @Test
    public void callWithInProgressAndJobIsFinished() throws Exception {
        String jobId = "1";
        SaltConnector saltConnector = Mockito.mock(SaltConnector.class);

        SaltJobRunner saltJobRunner = Mockito.mock(BaseSaltJobRunner.class);
        when(saltJobRunner.getJid()).thenReturn(JobId.jobId(jobId));
        when(saltJobRunner.getJobState()).thenCallRealMethod();
        doCallRealMethod().when(saltJobRunner).setJobState(any());

        SaltErrorResolver saltErrorResolver = Mockito.mock(SaltErrorResolver.class);
        when(saltConnector.getSaltErrorResolver()).thenReturn(saltErrorResolver);

        when(saltJobRunner.submit(any(SaltConnector.class))).thenReturn(jobId);
        saltJobRunner.setJobState(JobState.IN_PROGRESS);

        Set<String> targets = new HashSet<>();
        targets.add("10.0.0.1");
        targets.add("10.0.0.2");
        targets.add("10.0.0.3");
        when(saltJobRunner.getTargetHostnames()).thenReturn(targets);

        PowerMockito.mockStatic(SaltStates.class);
        PowerMockito.when(SaltStates.jobIsRunning(any(), any())).thenReturn(false);

        Multimap<String, Map<String, String>> missingNodesWithReason = ArrayListMultimap.create();
        Multimap<String, String> missingNodesWithResolvedReason = ArrayListMultimap.create();
        PowerMockito.when(SaltStates.jidInfo(any(), any(), any())).thenReturn(missingNodesWithReason);
        when(saltErrorResolver.resolveErrorMessages(missingNodesWithReason)).thenReturn(missingNodesWithResolvedReason);

        SaltJobIdTracker underTest = new SaltJobIdTracker(saltConnector, saltJobRunner);
        assertTrue(underTest.call());

        assertEquals(JobState.FINISHED, saltJobRunner.getJobState());

        PowerMockito.verifyStatic(SaltStates.class);
        SaltStates.jobIsRunning(any(), eq(jobId));
        checkTargets(targets, targetCaptor.getAllValues());
    }

    @Test
    public void callWithInProgressAndMissingNodes() throws Exception {
        String jobId = "1";
        try (SaltConnector saltConnector = Mockito.mock(SaltConnector.class)) {

            SaltJobRunner saltJobRunner = Mockito.mock(BaseSaltJobRunner.class);
            when(saltJobRunner.getJid()).thenReturn(JobId.jobId(jobId));
            when(saltJobRunner.getJobState()).thenCallRealMethod();
            doCallRealMethod().when(saltJobRunner).setJobState(any());
            when(saltJobRunner.getNodesWithError()).thenCallRealMethod();
            doCallRealMethod().when(saltJobRunner).setNodesWithError(any());
            when(saltJobRunner.submit(any(SaltConnector.class))).thenReturn(jobId);
            SaltErrorResolver saltErrorResolver = Mockito.mock(SaltErrorResolver.class);
            when(saltConnector.getSaltErrorResolver()).thenReturn(saltErrorResolver);
            saltJobRunner.setJobState(JobState.IN_PROGRESS);

            Set<String> targets = new HashSet<>();
            targets.add("10.0.0.1");
            targets.add("10.0.0.2");
            targets.add("10.0.0.3");
            when(saltJobRunner.getTargetHostnames()).thenReturn(targets);

            Multimap<String, Map<String, String>> missingNodesWithReason = ArrayListMultimap.create();
            Multimap<String, String> missingNodesWithResolvedReason = ArrayListMultimap.create();
            String missingMachine = "10.0.0.1";
            missingNodesWithReason.put(missingMachine, Collections.singletonMap("Name", "some-script.sh"));
            missingNodesWithResolvedReason.put(missingMachine, "Failed to execute: {Name=some-script.sh}");
            when(saltErrorResolver.resolveErrorMessages(missingNodesWithReason)).thenReturn(missingNodesWithResolvedReason);

            PowerMockito.mockStatic(SaltStates.class);
            PowerMockito.when(SaltStates.jobIsRunning(any(), any())).thenReturn(false);
            PowerMockito.when(SaltStates.jidInfo(any(SaltConnector.class), anyString(), any())).thenReturn(missingNodesWithReason);

            try {
                new SaltJobIdTracker(saltConnector, saltJobRunner).call();
                fail("should throw exception");
            } catch (CloudbreakOrchestratorFailedException e) {
                assertThat(e.getMessage(), allOf(containsString("Node: 10.0.0.1 Error(s): Failed to execute: {Name=some-script.sh}"),
                        containsString("Target:"), containsString("10.0.0.1"), containsString("10.0.0.2"),
                        containsString("10.0.0.3")));
            }

            PowerMockito.verifyStatic(SaltStates.class);
            SaltStates.jobIsRunning(any(), eq(jobId));
            checkTargets(targets, targetCaptor.getAllValues());
        }
    }

    @Test
    public void callWithMissingNodesUsingStderrFailures() throws Exception {
        String jobId = "1";
        try (SaltConnector saltConnector = Mockito.mock(SaltConnector.class)) {

            SaltJobRunner saltJobRunner = Mockito.mock(BaseSaltJobRunner.class);
            when(saltJobRunner.getJid()).thenReturn(JobId.jobId(jobId));
            when(saltJobRunner.getJobState()).thenCallRealMethod();
            doCallRealMethod().when(saltJobRunner).setJobState(any());
            when(saltJobRunner.getNodesWithError()).thenCallRealMethod();
            doCallRealMethod().when(saltJobRunner).setNodesWithError(any());
            when(saltJobRunner.submit(any(SaltConnector.class))).thenReturn(jobId);
            SaltErrorResolver saltErrorResolver = Mockito.mock(SaltErrorResolver.class);
            when(saltConnector.getSaltErrorResolver()).thenReturn(saltErrorResolver);
            saltJobRunner.setJobState(JobState.IN_PROGRESS);

            Set<String> targets = new HashSet<>();
            targets.add("10.0.0.1");
            targets.add("10.0.0.2");
            targets.add("10.0.0.3");
            when(saltJobRunner.getTargetHostnames()).thenReturn(targets);

            Multimap<String, Map<String, String>> missingNodesWithReason = ArrayListMultimap.create();
            Multimap<String, String> missingNodesWithResolvedReason = ArrayListMultimap.create();
            String missingMachine = "10.0.0.1";
            missingNodesWithReason.put(missingMachine, Map.of("Name", "/opt/salt/scripts/backup_db.sh", "Stderr", "Could not create backup"));
            missingNodesWithResolvedReason.put(missingMachine, "Could not create backup");
            when(saltErrorResolver.resolveErrorMessages(missingNodesWithReason)).thenReturn(missingNodesWithResolvedReason);

            PowerMockito.mockStatic(SaltStates.class);
            PowerMockito.when(SaltStates.jobIsRunning(any(), any())).thenReturn(false);
            PowerMockito.when(SaltStates.jidInfo(any(SaltConnector.class), anyString(), any())).thenReturn(missingNodesWithReason);

            try {
                new SaltJobIdTracker(saltConnector, saltJobRunner).call();
                fail("should throw exception");
            } catch (CloudbreakOrchestratorFailedException e) {
                assertThat(e.getMessage(), allOf(containsString("Node: 10.0.0.1 Error(s): Could not create backup"),
                        containsString("Target:"), containsString("10.0.0.1"), containsString("10.0.0.2"),
                        containsString("10.0.0.3")));
            }

            PowerMockito.verifyStatic(SaltStates.class);
            SaltStates.jobIsRunning(any(), eq(jobId));
            checkTargets(targets, targetCaptor.getAllValues());
        }
    }

    @Test
    public void callWithInProgressAndMissingNodesAndNoRetryOnFail() throws Exception {
        String jobId = "1";
        try (SaltConnector saltConnector = Mockito.mock(SaltConnector.class)) {

            SaltJobRunner saltJobRunner = Mockito.mock(BaseSaltJobRunner.class);
            when(saltJobRunner.getJid()).thenReturn(JobId.jobId(jobId));
            when(saltJobRunner.getJobState()).thenCallRealMethod();
            doCallRealMethod().when(saltJobRunner).setJobState(any());

            Multimap<String, String> missingNodesWithReason = ArrayListMultimap.create();
            String missingMachine = "10.0.0.1";
            String errorMessage = "Name: some-script.sh";
            missingNodesWithReason.put(missingMachine, errorMessage);
            when(saltJobRunner.getNodesWithError()).thenReturn(missingNodesWithReason);
            when(saltJobRunner.submit(any(SaltConnector.class))).thenReturn(jobId);
            saltJobRunner.setJobState(JobState.FAILED);

            Set<String> targets = Sets.newHashSet("10.0.0.1", "10.0.0.2", "10.0.0.3");
            when(saltJobRunner.getTargetHostnames()).thenReturn(targets);

            try {
                new SaltJobIdTracker(saltConnector, saltJobRunner, false).call();
                fail("should throw exception");
            } catch (CloudbreakOrchestratorTerminateException e) {
                assertThat(e.getMessage(), both(containsString(missingMachine)).and(containsString(errorMessage)));
            }

            checkTargets(targets, targetCaptor.getAllValues());
        }
    }

    @Test
    public void callWithNotStartedAndSlsWithError() throws Exception {
        String jobId = "1";
        try (SaltConnector saltConnector = Mockito.mock(SaltConnector.class)) {

            SaltJobRunner saltJobRunner = Mockito.mock(BaseSaltJobRunner.class);
            when(saltJobRunner.getJid()).thenReturn(JobId.jobId(jobId));
            when(saltJobRunner.getJobState()).thenCallRealMethod();
            doCallRealMethod().when(saltJobRunner).setJobState(any());
            when(saltJobRunner.getNodesWithError()).thenCallRealMethod();
            doCallRealMethod().when(saltJobRunner).setNodesWithError(any());
            when(saltJobRunner.submit(any(SaltConnector.class))).thenReturn(jobId);
            saltJobRunner.setJobState(JobState.NOT_STARTED);

            Set<String> targets = Sets.newHashSet("10.0.0.1", "10.0.0.2", "10.0.0.3");
            when(saltJobRunner.getTargetHostnames()).thenReturn(targets);

            PowerMockito.mockStatic(SaltStates.class);
            PowerMockito.when(SaltStates.jobIsRunning(any(), any())).thenReturn(false);
            PowerMockito.when(SaltStates.jidInfo(any(SaltConnector.class), anyString(), any()))
                    .thenThrow(new RuntimeException("Salt execution went wrong: saltErrorDetails"));
            RunningJobsResponse jobsResponse = new RunningJobsResponse();
            jobsResponse.setResult(List.of());
            PowerMockito.when(SaltStates.getRunningJobs(saltConnector)).thenReturn(jobsResponse);

            try {
                new SaltJobIdTracker(saltConnector, saltJobRunner).call();
                fail("should throw exception");
            } catch (CloudbreakOrchestratorFailedException e) {
                assertThat(e.getMessage(), containsString("Salt execution went wrong: saltErrorDetails"));
                assertThat(e.getMessage(), not(containsString("Exception")));
            }

            PowerMockito.verifyStatic(SaltStates.class);
            SaltStates.jobIsRunning(any(), eq(jobId));
            checkTargets(targets, targetCaptor.getAllValues());
        }
    }
}