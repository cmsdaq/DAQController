package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.Data;

import java.util.Date;

@Data
public class RecoveryJobStatus {

    Integer stepIndex;
    Date started;
    Date finished;

    String status;
    String rcmsStatus;

    /** How many times this step was executed */
    Integer timesExecuted;

}
