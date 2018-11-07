package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.Data;

/**
 * Response to expert. Data transfer object
 */
@Data
public class RecoveryResponse {

    /**
     * Result of the recovery request acceptance, may be accepted, rejected etc.
     */
    private String acceptanceDecision;

    /**
     * Id of the recovery request that was accepted or rejected
     */
    private Long recoveryId;

    /**
     * Id of the condition that generated recovery
     */
    private Long conditionId;

    /**
     * Id of the recovery procedure
     */
    private Long recoveryProcedureId;

    /**
     * Id of the condition that was a reason of rejection
     */
    private Long rejectedDueToConditionId;

    /**
     * Id of the same (other instance the same problem) condition it continues
     */
    private Long continuesTheConditionId;

}
