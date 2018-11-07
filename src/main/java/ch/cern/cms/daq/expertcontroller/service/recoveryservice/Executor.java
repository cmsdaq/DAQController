package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalResponse;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMEvent;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSM;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.IFSMListener;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.State;
import lombok.Builder;
import lombok.Getter;

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
     * Executor service. Will allow to run 1 job at the time.
     */
    protected ExecutorService executorService;

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


    @Override
    public List<String> start(RecoveryProcedure recoveryProcedure) {
        executedProcedure = recoveryProcedure;
        listener.setCurrentProcedure(recoveryProcedure);
        fsm.transition(FSMEvent.RecoveryStarts);
        return listener.getSummary();
    }

    @Override
    public void approveRecovery(ApprovalResponse approvalResponse) {

        // TODO: check if response concerns the same recovery
        boolean defaultJobContext = true;

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
        fsm.transition(FSMEvent.Finished);
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
        } catch (InterruptedException e) {
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
        } catch (InterruptedException e) {
            return FSMEvent.Interrupt;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return FSMEvent.Exception;
        } catch (TimeoutException e) {
            e.printStackTrace();
            return FSMEvent.Timeout;
        }
    }

    @Override
    public FSMEvent callObservationConsumer() {
        Future<FSMEvent> future = executorService.submit(() -> observationConsumer.get());
        try {
            return future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return FSMEvent.Interrupt;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return FSMEvent.Exception;
        }
    }

    @Override
    public void callStatusReportConsumer(RecoveryProcedure recoveryProcedure, List<String> report) {
        statusReportConsumer.accept(recoveryProcedure, report);
    }

}
