package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response to expert. Data transfer object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryResponse {

    /**
     * Result of the recovery request acceptance, may be accepted, rejected etc.
     */
    private String acceptanceDecision;

    /**
     * Id of the recovery procedure that was created (unless it was rejected)
     */
    private Long recoveryProcedureId;

    /**
     * Id of the condition that was a reason of rejection
     */
    private Long rejectedDueToConditionId;

    /**
     * Id of the same (other instance the same problem) condition it continues
     */
    private List<Long> continuesTheConditionIds;

}
