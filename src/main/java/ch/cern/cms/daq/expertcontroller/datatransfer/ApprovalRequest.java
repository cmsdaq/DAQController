package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashMap;
import java.util.List;

/**
 * Request sent from controller to dashboard in order to ask for approval of given recovery.
 */
@Data
@AllArgsConstructor
public class ApprovalRequest {
    private long recoveryProcedureId;
}
