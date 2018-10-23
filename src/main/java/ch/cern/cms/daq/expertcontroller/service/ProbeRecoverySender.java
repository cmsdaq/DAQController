package ch.cern.cms.daq.expertcontroller.service;

import ch.cern.cms.daq.expertcontroller.controller.ExpertController;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRequestDTO;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRequestStepDTO;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryRequestStep;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryResponse;
import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalResponse;
import ch.cern.cms.daq.expertcontroller.controller.DashboardController;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;

@Service
public class ProbeRecoverySender {

    @Autowired
    private ExpertController expertController;

    @Autowired
    private DashboardController dashboardController;

    @Value("${observe.period}")
    protected long observePeriod;

    private static final Logger logger = Logger.getLogger(ProbeRecoverySender.class);

    private static long problemId = 0;

    public void issueTestRecoverySequence(String subsystem) {

        RecoveryRequestDTO recoveryRequest1 = generateEmptyRecoveryRequest(subsystem);
        recoveryRequest1.getRecoverySteps().iterator().next().setIssueTTCHardReset(true);
        logger.info("Sending TTC Hard Reset reset");
        sendRequestAndApprove(recoveryRequest1);
        logger.info("TTC Hard Reset sent");

        try {
            Thread.sleep(observePeriod);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        RecoveryRequestDTO recoveryRequest2 = generateEmptyRecoveryRequest(subsystem);
        if(subsystem != null) {
            recoveryRequest2.getRecoverySteps().iterator().next().getGreenRecycle().add(subsystem);
        }

        recoveryRequest2.setWithInterrupt(true);
        logger.info("Requesting stop and start with ECAL green recycle");
        sendRequestAndApprove(recoveryRequest2);
        logger.info("Stop and start with ECAL green recycle sent");

    }

    private void sendRequestAndApprove(RecoveryRequestDTO recoveryRequest) {
        ResponseEntity<RecoveryResponse> response = expertController.requestRecovery(recoveryRequest);
        Long recovery1Id = response.getBody().getRecoveryProcedureId();

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ApprovalResponse ar = new ApprovalResponse();
        ar.setRecoveryId(recovery1Id);
        ar.setApproved(true);
        ar.setStep(0);

        logger.info("Sending test approval");
        dashboardController.approve(ar);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        expertController.conditionFinished(recoveryRequest.getProblemId());

    }

    private RecoveryRequestDTO generateEmptyRecoveryRequest(String subsystem) {

        final Long id = problemId++;
        String problemTitle = "Probe problem " + id;
        if(subsystem != null){
            problemTitle += " for subsystem " + subsystem;
        }
        RecoveryRequestDTO recoveryRequest = RecoveryRequestDTO.builder()
                .problemTitle(problemTitle)
                .problemId(id)
                .recoverySteps(new ArrayList<>())
                .build();

        RecoveryRequestStepDTO step1 = RecoveryRequestStepDTO.builder()
                .redRecycle(new HashSet<>())
                .greenRecycle(new HashSet<>())
                .fault(new HashSet<>())
                .reset(new HashSet<>())
                .build();

        recoveryRequest.getRecoverySteps().add(step1);

        return recoveryRequest;
    }
}
