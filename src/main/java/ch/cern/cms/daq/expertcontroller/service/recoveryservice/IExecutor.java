package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalResponse;
import ch.cern.cms.daq.expertcontroller.entity.Event;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMEvent;

import java.util.List;

/**
 * Executor interface. Responsible for executing recovery jobs.
 *
 * @see RecoveryProcedure
 */
public interface IExecutor {

    /**
     * Submit new job to execute
     *
     * @param recoveryJob
     * @return summary of actions performed in recovery
     */
    List<Event> start(RecoveryProcedure recoveryJob);


    /**
     * Submit new job to execute
     *
     * @param recoveryJob
     * @param wait flag indicating whether the request should be executed synchronously
     * @return summary of actions performed in recovery
     */
    List<Event> start(RecoveryProcedure recoveryJob, boolean wait);

    /**
     * Approve given recovery step
     *
     * @param approvalResponse
     */
    void approveRecovery(ApprovalResponse approvalResponse);

    /**
     * Interrupt recovery procedure
     */
    void interrupt();

    /**
     * Problematic condition finished
     */
    boolean finished();

    /**
     * Get current recovery acceptanceDecision
     */
    ExecutorStatus getStatus();

    /**
     * Get last recovery procedure.
     *
     * @return last recovery procedure. If executor is currently executing procedure it will be the one being executed.
     * If executor is Idle, it will be the last completed.
     */
    RecoveryProcedure getExecutedProcedure();


    /**
     * Call recovery job execution consumer
     *
     * @param recoveryJob recovery job to be executed
     * @return resulting event
     */
    FSMEvent callRecoveryExecutionConsumer(RecoveryJob recoveryJob);

    /**
     * Call recovery job approval consumer
     *
     * @param recoveryJob recovery job to be approved
     * @return resulting event
     */
    FSMEvent callApprovalRequestConsumer(RecoveryJob recoveryJob);

    /**
     * Call observation consumer
     *
     * @return resulting event
     */
    FSMEvent callObservationConsumer();


    /**
     * Call interruption consumer
     */
    void callInterruptConsumer();

    /**
     * Call status report consumer
     *
     * @param recoveryProcedure recovery procedure in context of which status is reported
     * @param report current recovery procedure acceptanceDecision
     */
    void callStatusReportConsumer(RecoveryProcedure recoveryProcedure, List<Event> report);

    FSMEvent forceSelectJob(int stepIndex);

    void setForceAccept(boolean forceAccept);

    boolean isForceAccept();

    void rcmsStatusUpdate(String status);
}
