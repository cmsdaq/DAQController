package ch.cern.cms.daq.expertcontroller.service;

import ch.cern.cms.daq.expertcontroller.controller.DashboardController;
import ch.cern.cms.daq.expertcontroller.controller.ExpertController;
import ch.cern.cms.daq.expertcontroller.datatransfer.*;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

@Service
public class ProbeRecoverySender {

    @Autowired
    private ExpertController expertController;

    @Autowired
    private DashboardController dashboardController;

    private static final Logger logger = Logger.getLogger(ProbeRecoverySender.class);

    private static long problemId = 0;

    public ResponseEntity<RecoveryResponse> issueTTCHardReset() {

        RecoveryRequest recoveryRequest1 = generateEmptyRecoveryRequest(1);
        recoveryRequest1.setAutomatedRecoveryEnabled(true);
        recoveryRequest1.setIsProbe(true);
        recoveryRequest1.setProblemTitle("Probe problem");
        RecoveryRequestStep step = recoveryRequest1.getRecoveryRequestSteps().iterator().next();
        step.setIssueTTCHardReset(true);
        step.setHumanReadable("TTC hard reset test job");
        logger.info("Sending TTC Hard Reset reset");
        ResponseEntity<RecoveryResponse> response = sendRecoveryRequest(recoveryRequest1);
        logger.info("TTC Hard Reset sent");

        return response;

    }


    public ResponseEntity<RecoveryResponse> issueRecovery(String subsystem){

        RecoveryRequest recoveryRequest2 = generateEmptyRecoveryRequest(2);
        recoveryRequest2.setIsProbe(true);
        recoveryRequest2.setAutomatedRecoveryEnabled(true);

        if(subsystem == null){
            recoveryRequest2.setProblemTitle("Probe problem without subsystem");
        } else{
            recoveryRequest2.setProblemTitle("Probe problem of " + subsystem);
        }

        Iterator<RecoveryRequestStep> iterator = recoveryRequest2.getRecoveryRequestSteps().iterator();

        RecoveryRequestStep step1 = iterator.next();
        RecoveryRequestStep step2 = iterator.next();

        step1.getGreenRecycle().add(subsystem);
        step1.setHumanReadable("Green recycle " + subsystem);
        step1.getReset().add("HCAL");
        step1.getFault().add(subsystem);

        step2.getRedRecycle().add(subsystem);
        step2.setHumanReadable("Red recycle " + subsystem);
        step1.getFault().add(subsystem);

        logger.info("Requesting stop and start with " +subsystem+ " green recycle");
        ResponseEntity<RecoveryResponse> response = sendRecoveryRequest(recoveryRequest2);
        logger.info("Stop and start with "+subsystem+" green recycle sent");
        return response;


    }

    private ResponseEntity<RecoveryResponse> sendRecoveryRequest(RecoveryRequest recoveryRequest) {
        ResponseEntity<RecoveryResponse> response = expertController.requestRecovery(recoveryRequest);
        logger.info("Recovery submitted." +
                            " Status: " + response.getStatusCode() +
                            " Acceptance decision: " + response.getBody().getAcceptanceDecision() +
                            " Recovery procedure: " + response.getBody().getRecoveryProcedureId());
        Long recovery1Id = response.getBody().getRecoveryProcedureId();

//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

//        ApprovalResponse ar = new ApprovalResponse();
//        ar.setRecoveryProcedureId(recovery1Id);
//        ar.setApproved(true);
//        ar.setStep(0);
//
//        logger.info("Sending test approval");
//        dashboardController.approve(ar);
//
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

//        expertController.conditionFinished(recoveryRequest.getProblemId());
        return response;

    }

    private RecoveryRequest generateEmptyRecoveryRequest(int steps) {

        final Long id = problemId++;
        RecoveryRequest recoveryRequest = RecoveryRequest.builder()
                .problemId(id)
                .recoveryRequestSteps(new ArrayList<>())
                .build();

        for(int i = 0; i < steps; i++) {
            RecoveryRequestStep step1 = RecoveryRequestStep.builder()
                    .redRecycle(new HashSet<>())
                    .greenRecycle(new HashSet<>())
                    .fault(new HashSet<>())
                    .reset(new HashSet<>())
                    .build();
            step1.setStepIndex(i);
            recoveryRequest.getRecoveryRequestSteps().add(step1);

        }
        return recoveryRequest;
    }
}
