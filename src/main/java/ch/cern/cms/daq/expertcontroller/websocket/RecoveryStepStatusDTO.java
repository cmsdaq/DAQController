package ch.cern.cms.daq.expertcontroller.websocket;

import lombok.Data;

import java.util.Date;

@Data
public class RecoveryStepStatusDTO {

    Integer stepIndex;
    Date started;
    Date finished;
    String status;
    String rcmsStatus;

    /** How many times this step was executed */
    Integer timesExecuted;

    @Override
    public String toString() {
        return "RecoveryStepStatusDTO{" +
                "stepIndex=" + stepIndex +
                ", started=" + started +
                ", finished=" + finished +
                ", status='" + status + '\'' +
                '}';
    }
}
