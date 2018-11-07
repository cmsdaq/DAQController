package ch.cern.cms.daq.expertcontroller.datatransfer;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecoveryServiceStatus {

    private String executorState;

    /**
     * Last submitted procedure. It can be still ongoing of finished in case executor is in idle state.
     */
    private RecoveryProcedureStatus lastProcedureStatus;
}
