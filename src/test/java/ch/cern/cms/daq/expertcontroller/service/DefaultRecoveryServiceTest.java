package ch.cern.cms.daq.expertcontroller.service;

import ch.cern.cms.daq.expertcontroller.controller.DashboardController;
import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalResponse;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRequestStep;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryResponse;
import ch.cern.cms.daq.expertcontroller.entity.Event;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.repository.RecoveryProcedureRepository;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.IExecutor;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.TestExecutorFactory;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMEvent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class DefaultRecoveryServiceTest {

    @MockBean
    private DashboardController dashboardController;


    @MockBean
    private RecoveryProcedureRepository recoveryProcedureRepository;


    @Autowired
    private DefaultRecoveryService recoveryService;

    public static Boolean recoveryJobShouldComplete;

    public static FSMEvent expectedObserveResult;

    @Before
    public void prepareMocks() {
        Answer<?> answer = new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                RecoveryProcedure recoveryProcedure = invocationOnMock.getArgumentAt(0, RecoveryProcedure.class);
                recoveryProcedure.setId(10001L);
                return null;
            }
        };

        given(this.dashboardController.approve(any())).willReturn(null);
        given(this.recoveryProcedureRepository.save(any(RecoveryProcedure.class))).willAnswer(answer);

        recoveryJobShouldComplete = null;
    }

    @Test
    public void idleTest() {
        Assert.assertEquals("Idle", recoveryService.getRecoveryServiceStatus().getExecutorState());
        Assert.assertNull(recoveryService.getRecoveryServiceStatus().getLastProcedureStatus());
    }

    @Test
    public void simpleProcedureTest() {

        recoveryJobShouldComplete = true;
        expectedObserveResult = FSMEvent.NoEffect;

        RecoveryRequest recoveryRequest = RecoveryRequest.builder()
                .problemTitle("Test problem")
                .recoverySteps(Arrays.asList(
                        RecoveryRequestStep.builder()
                                .redRecycle(new HashSet<>(Arrays.asList("T")))
                                .humanReadable("Test 1")
                                .build())).build();

        RecoveryResponse recoveryResponse = recoveryService.submitRecoveryRequest(recoveryRequest);
        long procedureId = recoveryResponse.getRecoveryProcedureId();
        Assert.assertEquals("accepted", recoveryResponse.getAcceptanceDecision());

        Assert.assertEquals("AwaitingApproval", recoveryService.getRecoveryServiceStatus().getExecutorState());

        recoveryService.submitApprovalDecision(
                ApprovalResponse.builder()
                        .approved(true)
                        .recoveryProcedureId(procedureId)
                        .step(0) // TODO: check what happens when different steps are selected
                        .build());

        Assert.assertEquals("Idle", recoveryService.getRecoveryServiceStatus().getExecutorState());


        Assert.assertEquals(Arrays.asList(
                "Job Test 1 accepted",
                "Job Test 1 completed",
                "Job Test 1 didn't fix the problem",
                "Job not found, recovery failed"),
                            recoveryService.getRecoveryServiceStatus().getLastProcedureStatus().getActionSummary());

    }

    @Test
    public void twoJobProcedureTest() {


        recoveryJobShouldComplete = true;

        Assert.assertEquals("Idle", recoveryService.getRecoveryServiceStatus().getExecutorState());

        RecoveryRequest recoveryRequest = RecoveryRequest.builder()
                .problemTitle("Two steps procedure")
                .recoverySteps(Arrays.asList(
                        RecoveryRequestStep.builder()
                                .humanReadable("Test 1")
                                .stepIndex(0)
                                .build(),

                        RecoveryRequestStep.builder()
                                .humanReadable("Test 2")
                                .stepIndex(1)
                                .build())).build();

        RecoveryResponse recoveryResponse = recoveryService.submitRecoveryRequest(recoveryRequest);
        long procedureId = recoveryResponse.getRecoveryProcedureId();
        Assert.assertEquals("accepted", recoveryResponse.getAcceptanceDecision());

        Assert.assertEquals("AwaitingApproval", recoveryService.getRecoveryServiceStatus().getExecutorState());


        expectedObserveResult = FSMEvent.NoEffect;
        recoveryService.submitApprovalDecision(
                ApprovalResponse.builder()
                        .approved(true)
                        .recoveryProcedureId(procedureId)
                        .step(0)
                        .build());


        Assert.assertEquals("AwaitingApproval",
                            recoveryService.getRecoveryServiceStatus().getExecutorState());
        Assert.assertEquals(Arrays.asList(
                "Job Test 1 accepted",
                "Job Test 1 completed",
                "Job Test 1 didn't fix the problem"),
                            recoveryService.getRecoveryServiceStatus().getLastProcedureStatus().getActionSummary());


        expectedObserveResult = FSMEvent.Finished;
        recoveryService.submitApprovalDecision(
                ApprovalResponse.builder()
                        .approved(true)
                        .recoveryProcedureId(procedureId)
                        .step(1)
                        .build());


        Assert.assertEquals("Idle",
                            recoveryService.getRecoveryServiceStatus().getExecutorState());

        Assert.assertNotNull(recoveryService.getRecoveryServiceStatus().getLastProcedureStatus());
        Assert.assertEquals(Arrays.asList(
                "Job Test 1 accepted",
                "Job Test 1 completed",
                "Job Test 1 didn't fix the problem",
                "Job Test 2 accepted",
                "Job Test 2 completed",
                "Recovery procedure completed successfully"),
                            recoveryService.getRecoveryServiceStatus()
                                    .getLastProcedureStatus().getActionSummary());

    }

}

@Profile("test")
@Configuration
class MockServicesProvider {

    Logger logger = LoggerFactory.getLogger(DefaultRecoveryServiceTest.class);

    @Bean
    public IExecutor executorService() {
        IExecutor executor = TestExecutorFactory.build(
                TestExecutorFactory.approvalConsumerThatNeverAccepts,
                recoveryJob -> {

                    if (DefaultRecoveryServiceTest.recoveryJobShouldComplete == null) {
                        throw new IllegalStateException("Control recoveryJobShouldComplete parameter");
                    }

                    if (DefaultRecoveryServiceTest.recoveryJobShouldComplete) {
                        return FSMEvent.JobCompleted;
                    } else {
                        return null;
                    }

                },
                (r, s) -> {
                    System.out.println("Result: " + s);

                    List<Event> eventList = s.stream()
                            .map(e -> Event.builder().content(e).build())
                            .collect(Collectors.toList());

                    r.setEventSummary(eventList);

                },
                () -> DefaultRecoveryServiceTest.expectedObserveResult,
                1,
                1
        );
        return executor;

    }
}