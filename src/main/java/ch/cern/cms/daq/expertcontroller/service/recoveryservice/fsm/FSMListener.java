package ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.IExecutor;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder
public class FSMListener implements IFSMListener {


    private IExecutor executor;

    @Getter
    private RecoveryProcedure currentProcedure;

    @Getter
    private RecoveryJob currentJob;

    List<String> reportSteps;


    private static Logger logger = LoggerFactory.getLogger(FSMListener.class);

    public void setExecutor(IExecutor executor) {
        this.executor = executor;
    }


    @Override
    public void setCurrentProcedure(RecoveryProcedure currentProcedure) {
        this.currentProcedure = currentProcedure;
    }

    @Override
    public List<String> getSummary() {
        return reportSteps;
    }

    private FSMEvent selectNextJob() {
        RecoveryJob nextJob = currentProcedure.getNextJob();

        if(currentProcedure.getExecutedJobs() == null){
            currentProcedure.setExecutedJobs(new ArrayList<>());
        }
        currentProcedure.getExecutedJobs().add(nextJob);

        if (nextJob != null) {

            logger.info("Next job found: " + nextJob.getStepIndex() + ", "+ nextJob.getJob());
            currentJob = nextJob;
            return FSMEvent.NextJobFound;
        } else {

            logger.info("Next job not found");
            return FSMEvent.NextJobNotFound;
        }

    }

    @Override
    public FSMEvent onStart() {

        reportSteps = new ArrayList<>();
        logger.info("Recovery of current procedure "
                            + currentProcedure.getId() + ":"
                            + currentProcedure.getProblemTitle() + " starts");
        return selectNextJob();
    }

    @Override
    public FSMEvent onJobAccepted() {
        logger.info("Job accepted. Passing to job consumer");

        reportSteps.add("Job " + currentJob.getJob() + " accepted");
        return executor.callRecoveryExecutionConsumer(currentJob);
    }

    @Override
    public FSMEvent onJobCompleted() {

        // TODO: need to provide some better reporting that simple string
        reportSteps.add("Job " + currentJob.getJob() + " completed");
        return executor.callObservationConsumer();
    }

    @Override
    public FSMEvent onJobNoEffect() {

        reportSteps.add("Job " + currentJob.getJob() + " didn't fix the problem");
        return selectNextJob();
    }

    @Override
    public FSMEvent onRecoveryFailed() {
        reportSteps.add("Recovery procedure failed");
        finishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onReportStatus() {

        executor.callStatusReportConsumer(currentProcedure, reportSteps);
        return null;
    }

    @Override
    public FSMEvent onNextJobNotFound() {
        logger.info("Next job not found");
        reportSteps.add("Next job not found, recovery failed");
        finishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onNextJobFound() {
        logger.info("Next job found: " + currentJob.getJob() + " Passing to approval request consumer");
        return executor.callApprovalRequestConsumer(currentJob);
    }

    @Override
    public FSMEvent onTimeout() {
        reportSteps.add("Job: " + currentJob.getJob() + " times out");
        finishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onException() {
        reportSteps.add("Recovery procedure finished with exception");
        finishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onFinished() {
        reportSteps.add("Recovery procedure completed successfully");
        finishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onInterrupted() {
        //TODO: stop any ongoing action
        reportSteps.add("Recovery procedure has been interrupted");
        finishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onCancelled() {
        reportSteps.add("Recovery procedure has been cancelled");
        finishProcedure();
        return FSMEvent.ReportStatus;
    }

    private void finishProcedure(){
        currentProcedure.setEnd(OffsetDateTime.now());
        currentProcedure.setState(executor.getStatus().getState().toString());

    }

}
