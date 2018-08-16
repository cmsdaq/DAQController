package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.api.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecord;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecordRepository;
import ch.cern.cms.daq.expertcontroller.websocket.DashboardController;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Holds the FSM of the recovery controller. Updates Recovery records in database. Sends requests to Dashboard via websocket.
 */
@Component("recoverySequenceController")
public class RecoverySequenceController {

    @Autowired
    private RecoveryRecordRepository recoveryRecordRepository;

    @Autowired
    private DashboardController dashboardController;

    @Autowired
    private RecoveryManager recoveryManager;

    /**
     * Period of time to observe system in order to decide if recovery should be continued of finished
     */
    public final static int observePeriod = 20000;

    private final static Logger logger = Logger.getLogger(RecoverySequenceController.class);

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    private RecoveryStatus currentStatus;


    public RecoveryRecord getMainRecord() {
        return mainRecord;
    }

    public void setMainRecord(RecoveryRecord mainRecord) {
        this.mainRecord = mainRecord;
    }

    private RecoveryRecord mainRecord;
    private RecoveryRecord current;

    public void start(RecoveryRequest recoveryRequest) {

        currentStatus = RecoveryStatus.AwaitingApproval;

        startRecoveryRecords("Waiting for approval", recoveryRequest.getProblemId(), recoveryRequest.getProblemTitle());
    }

    public void preempt(RecoveryRequest recoveryRequest) {

        if(recoveryRequest.getProblemId() == null){
            throw new IllegalArgumentException("Cannot process recovery request without problem id");
        }

        currentStatus = RecoveryStatus.AwaitingApproval;

        /* First finish current recovery records */
        finishRecoveryRecords();

        /* Than start new recovery records */
        startRecoveryRecords("Waiting for approval", recoveryRequest.getProblemId(), recoveryRequest.getProblemTitle());

    }



    public void continueSame(RecoveryRequest recoveryRequest){

        if(recoveryRequest.getProblemId() == null){
            throw new IllegalArgumentException("Cannot process recovery request without problem id");
        }

        currentStatus = RecoveryStatus.AwaitingApproval;

        finishAndStartNewCurrent("Waiting for approval",recoveryRequest.getProblemId(), "Waiting for operator approval");
    }


    public void end() {

        currentStatus = RecoveryStatus.Idle;
        finishRecoveryRecords();
        recoveryManager.endRecovery();

    }

    public void accept(int stepIndex, String stepDescription) {

        currentStatus = RecoveryStatus.RecoveryStep;
        String suffix;
        int humanReadableStepIndex = stepIndex + 1;
        if(humanReadableStepIndex == 1){
            suffix = "st";
        } else if (humanReadableStepIndex == 2){
            suffix = "nd";
        }else if (humanReadableStepIndex == 3){
            suffix = "rd";
        }  else {
            suffix = "th";
        }
        finishAndStartNewCurrent("Executing " + humanReadableStepIndex + suffix + " step", null, stepDescription);

    }

    public void stepCompleted(final Long initiatingProblem) {

        currentStatus = RecoveryStatus.Observe;

        finishAndStartNewCurrent("Observing ..", null, "Observing system for for response " + observePeriod + " ms");

        final RecoverySequenceController controller = this;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                if(!recoveryManager.receivedPreemption(initiatingProblem)) {
                    /* Recovery will be finished if nothing happens during observe period - no other requests from Expert - controller still in observe period, but:
                     * - condition has been finished before
                     * - condition has not been finished before
                     */
                    if (RecoveryStatus.Observe == controller.getCurrentStatus()) {

                        logger.info("No recovery request received during observe period and... ");

                        if (!recoveryManager.getOngoingProblems().contains(initiatingProblem)) {
                            logger.info("... causing problem is no longer active - finishing the recovery");
                            controller.end();
                        } else {
                            logger.info("... causing problem (" + initiatingProblem + ") is still on unfinished list (" + recoveryManager.getOngoingProblems() + ") - the recovery action didn't change anything - continue as the same problem");
                            //controller.continueSame(recoveryRequest);
                            recoveryManager.continueSameProblem();
                        }

                    } else {
                        logger.info("Some recovery request received during observe period");
                    }
                } else{
                    logger.info("Ignore proceeding steps of problem " + initiatingProblem + ", it was preempted during observe period");
                }
            }
        };


        if(!recoveryManager.receivedPreemption(initiatingProblem)) {
            finishAndStartNewCurrent("Observing ..", null, "Observing system for for response " + observePeriod + " ms");
            executor.schedule(runnable, observePeriod, TimeUnit.MILLISECONDS);
        } else{
            logger.info("Ignore proceeding steps of problem " + initiatingProblem + ", it was preempted before observe period started");
        }

    }

    public RecoveryStatus getCurrentStatus() {
        return currentStatus;
    }

    private void finishRecoveryRecords(){

        Date requestReceivedDate = new Date();

        /* First finish current recovery records */
        mainRecord.setEnd(requestReceivedDate);
        current.setEnd(requestReceivedDate);
        recoveryRecordRepository.save(mainRecord);
        recoveryRecordRepository.save(current);
    }




    private void startRecoveryRecords(String currentTitle, Long problemId, String problemTitle){

        Date requestReceivedDate = new Date();
        mainRecord = new RecoveryRecord();
        current = new RecoveryRecord();
        mainRecord.setStart(requestReceivedDate);
        mainRecord.setName("Recovery of " + problemTitle);
        mainRecord.setRelatedConditions(new LinkedHashSet<>());
        mainRecord.getRelatedConditions().add(problemId);

        current.setStart(requestReceivedDate);
        current.setName(currentTitle);
        recoveryRecordRepository.save(mainRecord);
        recoveryRecordRepository.save(current);
    }

    private void finishAndStartNewCurrent(String currentTitle, Long newConditionId, String currentDescription){

        if(newConditionId != null) {
            mainRecord.getRelatedConditions().add(newConditionId);
        }

        Date requestReceivedDate = new Date();

        current.setEnd(requestReceivedDate);
        recoveryRecordRepository.save(current);

        current = new RecoveryRecord();

        current.setStart(requestReceivedDate);
        current.setName(currentTitle);
        current.setDescription(currentDescription);
        recoveryRecordRepository.save(current);
    }


}

