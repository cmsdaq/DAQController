package ch.cern.cms.daq.expertcontroller;

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

    public void start(Long conditionId) {

        currentStatus = RecoveryStatus.AwaitingApproval;

        startRecoveryRecords("Waiting for approval", conditionId);
    }

    public void preempt(Long conditionId) {

        currentStatus = RecoveryStatus.AwaitingApproval;

        /* First finish current recovery records */
        finishRecoveryRecords();

        /* Than start new recovery records */
        startRecoveryRecords("Waiting for approval", conditionId);

    }



    public void continueSame(Long conditionId){

        currentStatus = RecoveryStatus.AwaitingApproval;

        finishAndStartNewCurrent("Waiting for approval",conditionId);
    }


    public void end() {

        currentStatus = RecoveryStatus.Idle;
        finishRecoveryRecords();
        recoveryManager.endRecovery();

    }

    public void accept() {

        currentStatus = RecoveryStatus.RecoveryStep;
        finishAndStartNewCurrent("Executing step ..");

    }

    public void stepCompleted() {

        currentStatus = RecoveryStatus.Observe;

        finishAndStartNewCurrent("Observing ..");

        final RecoverySequenceController controller = this;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                if (RecoveryStatus.Observe == controller.getCurrentStatus()) {
                    logger.info("Nothing happened during observe period, finishing condition");
                    controller.end();
                } else {
                    logger.info("Other thing happend during observe period");
                }
            }
        };
        executor.schedule(runnable, observePeriod, TimeUnit.MILLISECONDS);

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




    private void startRecoveryRecords(String currentTitle, Long conditionId){
        Date requestReceivedDate = new Date();
        mainRecord = new RecoveryRecord();
        current = new RecoveryRecord();
        mainRecord.setStart(requestReceivedDate);
        mainRecord.setName("Recovery of ..");
        mainRecord.setRelatedConditions(new LinkedHashSet<>());
        mainRecord.getRelatedConditions().add(conditionId);

        current.setStart(requestReceivedDate);
        current.setName(currentTitle);
        recoveryRecordRepository.save(mainRecord);
        recoveryRecordRepository.save(current);
    }

    private void finishAndStartNewCurrent(String currentTitle, Long conditionId){

        mainRecord.getRelatedConditions().add(conditionId);
        finishAndStartNewCurrent(currentTitle);
    }

    private void finishAndStartNewCurrent(String currentTitle){

        Date requestReceivedDate = new Date();


        current.setEnd(requestReceivedDate);
        recoveryRecordRepository.save(current);

        current = new RecoveryRecord();

        current.setStart(requestReceivedDate);
        current.setName(currentTitle);
        recoveryRecordRepository.save(current);
    }
}

