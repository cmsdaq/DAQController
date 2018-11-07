package ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.IExecutor;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Builder
public class FSMListener implements IFSMListener {


    private IExecutor executor;

    private RecoveryProcedure currentProcedure;
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

        if (nextJob != null) {
            currentJob = nextJob;
            return FSMEvent.NextJobFound;
        } else {
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
        return null;
    }

    @Override
    public FSMEvent onReportStatus() {
        executor.callStatusReportConsumer(currentProcedure, reportSteps);
        return null;
    }

    @Override
    public FSMEvent onNextJobNotFound() {
        logger.info("Next job not found");
        reportSteps.add("Job not found, recovery failed");
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
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onException() {
        return null;
    }

    @Override
    public FSMEvent onFinished() {
        reportSteps.add("Recovery procedure finished successfully");
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onInterrupted() {
        //TODO: stop any ongoing action
        reportSteps.add("Interrupted");
        return FSMEvent.ReportStatus;
    }

}
