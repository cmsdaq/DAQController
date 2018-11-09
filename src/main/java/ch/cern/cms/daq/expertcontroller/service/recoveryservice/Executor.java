package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalResponse;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSM;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMEvent;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.IFSMListener;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.State;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Responsible for executing recovery jobs. Holds FSM. Defines what actions are taken on given FSM states.
 */
@Builder
public class Executor implements IExecutor {

    /**
     * FSM of recovery procedure. Makes sure that the recovery procedure follows defined transition model.
     */
    protected FSM fsm;

    /**
     * Listener of the FSM. Collects FSM transition events.
     */
    protected IFSMListener listener;


    /**
     * Consumer of the recovery job. Called when there is job to execute.
     *
     * @see RecoveryJob
     */
    protected Function<RecoveryJob, FSMEvent> jobConsumer;

    /**
     * Consumer of the approval request. Called when job needs approval.
     */
    protected Function<RecoveryJob, FSMEvent> jobApprovalConsumer;

    /**
     * Consumer of system observation. Called when system needs to be observed.
     */
    protected Supplier<FSMEvent> observationConsumer;

    /**
     * Consumer of recovery report. Called when recovery procedure is finished or is updated.
     */
    protected BiConsumer<RecoveryProcedure, List<String>> statusReportConsumer;

    /**
     * Thread pool that will execute jobs. Eg. with one thread to allow to run 1 job at the time.
     */
    protected ExecutorService executorService;

    protected ScheduledThreadPoolExecutor delayedFinishedSignals;

    /**
     * Timeout period to approve the recovery job.
     */
    protected Integer approvalTimeout;

    /**
     * Timeout period for recovery job execution.
     */
    protected Integer executionTimeout;

    @Getter
    protected RecoveryProcedure executedProcedure;

    private static Logger logger = LoggerFactory.getLogger(Executor.class);


    @Override
    public List<String> start(RecoveryProcedure recoveryProcedure) {
        executedProcedure = recoveryProcedure;
        listener.setCurrentProcedure(recoveryProcedure);
        fsm.transition(FSMEvent.RecoveryStarts);
        return listener.getSummary();
    }

    @Override
    public void approveRecovery(ApprovalResponse approvalResponse) {

        if(executedProcedure == null){
            throw new IllegalStateException("Received approval response when executor is in Idle state");
        }

        if(listener.getCurrentJob() == null){
            throw new IllegalStateException("Received approval when executor has no current job");
        }

        Long defaultProcedureId = executedProcedure.getId();
        Integer defaultStepIndex = listener.getCurrentJob().getStepIndex();

        logger.info("Default procedure: " + defaultProcedureId + " with step:" + defaultStepIndex);

        // 1. check if response concerns the same recovery procedure and job
        boolean defaultJobContext =
                defaultProcedureId.equals(approvalResponse.getRecoveryProcedureId())
                        && defaultStepIndex.equals(approvalResponse.getStep());

        /* default job accepted */
        if (defaultJobContext && approvalResponse.getApproved()) {
            fsm.transition(FSMEvent.JobAccepted);
        }

        /* default job rejected */
        else if (defaultJobContext && !approvalResponse.getApproved()) {
            fsm.transition(FSMEvent.JobRejected);
        }

        /* other job accepted */
        else if (!defaultJobContext && approvalResponse.getApproved()) {
            fsm.transition(FSMEvent.OtherJobAccepted);
        }

        /* other job rejected */
        else if (!defaultJobContext && !approvalResponse.getApproved()) {
            // don't do anything
        }
    }

    @Override
    public void interrupt() {
        fsm.transition(FSMEvent.Interrupt);
    }

    @Override
    public void finished() {
        if(fsm.getState() == State.Observe) {
            fsm.transition(FSMEvent.Finished);
        } else if (fsm.getState() == State.Recovering){
            delayedFinishedSignals.schedule(()->this.finished(),1,TimeUnit.SECONDS);
        } else if (fsm.getState() == State.AwaitingApproval || fsm.getState() == State.SelectingJob){
            fsm.transition(FSMEvent.FinishedByItself);
        }
    }

    @Override
    public ExecutorStatus getStatus() {
        State currentState = fsm.getState();
        List<String> actionSummary = listener.getSummary();
        return ExecutorStatus.builder().actionSummary(actionSummary).state(currentState).build();
    }


    @Override
    public FSMEvent callApprovalRequestConsumer(RecoveryJob recoveryJob) {

        Future<FSMEvent> future = executorService.submit(() -> jobApprovalConsumer.apply(recoveryJob));
        try {
            return future.get(approvalTimeout, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            return FSMEvent.Interrupt;
        } catch (ExecutionException e) {
            return FSMEvent.Exception;
        } catch (TimeoutException e) {
            return FSMEvent.Timeout;
        }
    }

    @Override
    public FSMEvent callRecoveryExecutionConsumer(RecoveryJob recoveryJob) {
        Future<FSMEvent> future = executorService.submit(() -> jobConsumer.apply(recoveryJob));
        try {
            return future.get(executionTimeout, TimeUnit.SECONDS);
        }
        // exception will be thrown only on external interrupt
        catch (InterruptedException e) {
            logger.info("Recovery job timeouts. Limit is " + executionTimeout + " seconds");
            return FSMEvent.Interrupt;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return FSMEvent.Exception;
        } catch (TimeoutException e) {
            logger.info("Recovery job timeouts. Limit is " + executionTimeout + " seconds");
            return FSMEvent.Timeout;
        }
    }

    @Override
    public FSMEvent callObservationConsumer() {
        Future<FSMEvent> future = executorService.submit(() -> observationConsumer.get());
        try {
            return future.get();
        }
        // exception will be thrown whenever external interrupt comes or finish signal arrives on observation period
        catch (InterruptedException e) {

            // external interrupt during observe period
            if(fsm.getState() == State.Observe)
                return FSMEvent.Interrupt;
            // finish signal
            else
                return null;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return FSMEvent.Exception;
        }
    }

    @Override
    public void callStatusReportConsumer(RecoveryProcedure recoveryProcedure, List<String> report) {
        recoveryProcedure.setEnd(OffsetDateTime.now());
        statusReportConsumer.accept(recoveryProcedure, report);
    }

}
