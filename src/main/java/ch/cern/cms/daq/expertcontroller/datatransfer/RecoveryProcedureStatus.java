package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Status of the recovery. This POJO is used to communicate current state of recovery to the dashboard.
 */
@Data
@Builder
public class RecoveryProcedureStatus {

    /**
     * Id of the recovery procedure. Identifies the recovery as understood by controller
     * (as oppose to problem id - given by expert, and recovery request id - given by controller on every request from
     * expert, including subsequent request to the same problem)
     */
    Long id;

    /**
     * Action summary. What has been already done in this recovery.
     */
    List<String> actionSummary;

    /**
     * Final status of the Recovery Procedure, one of FSM final entries (completed/failed/cancelled)
     *
     * @see ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.State
     */
    String finalStatus;

    /**
     * Statuses of individual jobs.
     */
    List<RecoveryJobStatus> jobStatuses;

    /**
     * Date when recovery was started
     */
    Date startDate;

    /**
     * Date when recovery was finished
     */
    Date endDate;

    /**
     * Related condition ids.
     */
    List<Long> conditionIds;
}
