package ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm;

import ch.cern.cms.daq.expertcontroller.entity.Event;
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
import java.util.function.Consumer;

/**
 * This class listens to events in FSM. It is only reactive. It doesn't initiate any actions.
 * It's purpose is to observe, report, persit events and transitions
 */
@Builder
public class FSMListener implements IFSMListener {


    private IExecutor executor;

    @Getter
    private RecoveryProcedure currentProcedure;

    @Getter
    private RecoveryJob currentJob;

    List<Event> reportSteps;

    private Consumer<RecoveryProcedure> persistResultsConsumer;

    private Consumer<RecoveryProcedure> onUpdateConsumer;

    private static Logger logger = LoggerFactory.getLogger(FSMListener.class);

    public void setExecutor(IExecutor executor) {
        this.executor = executor;
    }

    public List<Event> getSummary() {
        return reportSteps;
    }

    @Override
    public void setCurrentProcedure(RecoveryProcedure currentProcedure) {
        this.currentProcedure = currentProcedure;
    }

    private FSMEvent selectNextJob() {
        if (currentProcedure.getProcedure() == null) {
            throw new IllegalStateException("Recovery procedure has no steps defined");
        }

        RecoveryJob nextJob = currentProcedure.getNextJob();

        if (currentProcedure.getExecutedJobs() == null) {
            currentProcedure.setExecutedJobs(new ArrayList<>());
        }

        if (nextJob != null) {
            logger.info("Next job found: " + nextJob.getStepIndex() + ", " + nextJob.getJob());

            currentProcedure.getExecutedJobs().add(nextJob);
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

        OffsetDateTime startTime = OffsetDateTime.now();

        reportSteps.add(Event.builder()
                                .content("Procedure starts")
                                .date(startTime)
                                .type("processing")
                                .build());


        currentProcedure.setStart(startTime);
        onUpdateProcedure();

        return selectNextJob();
    }

    @Override
    public FSMEvent onJobAccepted() {
        logger.info("Job accepted. Passing to job consumer");

        reportSteps.add(Event.builder()
                                .content("Job " + currentJob.getJob() + " accepted")
                                .date(OffsetDateTime.now())
                                .type("processing")
                                .build());
        currentJob.setStart(OffsetDateTime.now());

        currentJob.setStatus(State.Recovering.toString());

        currentJob.setEnd(null);
        onUpdateProcedure();
        return executor.callRecoveryExecutionConsumer(currentJob);
    }

    @Override
    public FSMEvent onJobCompleted() {

        reportSteps.add(Event.builder()
                                .content("Job " + currentJob.getJob() + " completed")
                                .date(OffsetDateTime.now())
                                .type("processing")
                                .build());

        currentJob.setEnd(OffsetDateTime.now());


        currentJob.setStatus(State.Completed.toString());

        onUpdateProcedure();
        return executor.callObservationConsumer();
    }

    @Override
    public FSMEvent onJobNoEffect() {

        reportSteps.add(Event.builder()
                                .content("Job " + currentJob.getJob() + " didn't fix the problem")
                                .date(OffsetDateTime.now())
                                .type("processing")
                                .build());

        currentJob.setStatus(State.Completed.toString());
        onUpdateProcedure();
        return selectNextJob();
    }

    @Override
    public FSMEvent onRecoveryFailed() {
        reportSteps.add(Event.builder()
                                .content("Recovery procedure failed")
                                .date(OffsetDateTime.now())
                                .type("processing")
                                .build());
        onUpdateProcedure();
        onFinishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onReportStatus() {

        executor.callStatusReportConsumer(currentProcedure, reportSteps);

        if(onUpdateConsumer != null)
            onUpdateConsumer.accept(currentProcedure);

        return null;
    }

    @Override
    public FSMEvent onNextJobNotFound() {
        logger.info("Next job not found");
        reportSteps.add(Event.builder()
                                .content("Next job not found, recovery failed")
                                .date(OffsetDateTime.now())
                                .type("processing")
                                .build());
        onUpdateProcedure();
        onFinishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onNextJobFound() {
        logger.info("Next job found: " + currentJob.getJob() + " Passing to approval request consumer");

        if(onUpdateConsumer != null)
            onUpdateConsumer.accept(currentProcedure);

        return executor.callApprovalRequestConsumer(currentJob);
    }

    @Override
    public FSMEvent onTimeout() {
        reportSteps.add(Event.builder()
                                .content("Job: " + currentJob.getJob() + " times out")
                                .date(OffsetDateTime.now())
                                .type("timeout")
                                .build());
        onUpdateProcedure();
        onFinishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onException() {
        reportSteps.add(Event.builder()
                                .content("Recovery procedure finished with exception")
                                .date(OffsetDateTime.now())
                                .type("exception")
                                .build());

        if(currentJob != null && currentJob.getEnd() == null){
            currentJob.setEnd(OffsetDateTime.now());
            currentJob.setStatus(State.Failed.toString());
        }
        onUpdateProcedure();
        onFinishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onJobException() {
        currentJob.setEnd(OffsetDateTime.now());
        currentJob.setStatus(State.Failed.toString());

        reportSteps.add(Event.builder()
                                .content("Job "+ currentJob.getJob()+" finished with exception")
                                .date(OffsetDateTime.now())
                                .type("exception")
                                .build());


        onUpdateProcedure();
        return selectNextJob();

    }

    @Override
    public FSMEvent onFinished() {
        reportSteps.add(Event.builder()
                                .content("Recovery procedure completed successfully")
                                .date(OffsetDateTime.now())
                                .type("finish")
                                .build());
        onUpdateProcedure();
        onFinishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onInterrupted() {
        reportSteps.add(Event.builder()
                                .content("Recovery procedure has been interrupted")
                                .date(OffsetDateTime.now())
                                .type("interrupt")
                                .build());

        if (currentJob != null && currentJob.getEnd() == null) {
            currentJob.setEnd(OffsetDateTime.now());
            currentJob.setStatus(State.Cancelled.toString());
        }
        onUpdateProcedure();
        onFinishProcedure();
        return FSMEvent.ReportStatus;
    }

    @Override
    public FSMEvent onCancelled() {
        reportSteps.add(Event.builder()
                                .content("Recovery procedure has been cancelled")
                                .date(OffsetDateTime.now())
                                .type("cancellation")
                                .build());
        onUpdateProcedure();
        onFinishProcedure();
        return FSMEvent.ReportStatus;
    }

    private void onFinishProcedure() {
        currentProcedure.setEnd(OffsetDateTime.now());
        currentProcedure.setState(executor.getStatus().getState().toString());

        if (persistResultsConsumer != null)
            persistResultsConsumer.accept(currentProcedure);

        if(onUpdateConsumer != null)
            onUpdateConsumer.accept(currentProcedure);

    }

    private void onUpdateProcedure() {
        currentProcedure.setState(executor.getStatus().getState().toString());
        currentProcedure.setEventSummary(getSummary());

        if (persistResultsConsumer != null)
            persistResultsConsumer.accept(currentProcedure);

        if(onUpdateConsumer != null)
            onUpdateConsumer.accept(currentProcedure);


    }

}
