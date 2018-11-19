package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecoveryJobStatus {

    Integer stepIndex;
    Date started;
    Date finished;

    String status;

    String rcmsStatus;

    /** How many times this step was executed */
    Integer timesExecuted;

}
