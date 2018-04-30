package ch.cern.cms.daq.expertcontroller.websocket;

import java.util.Date;

public class RecoveryStepStatus {

    Integer stepIndex;
    Date started;
    Date finished;
    String status;
    String rcmsStatus;


    /** How many times this step was executed */
    Integer timesExecuted;

    public Date getStarted() {
        return started;
    }

    public void setStarted(Date started) {
        this.started = started;
    }

    public Date getFinished() {
        return finished;
    }

    public void setFinished(Date finished) {
        this.finished = finished;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(Integer stepIndex) {
        this.stepIndex = stepIndex;
    }


    public String getRcmsStatus() {
        return rcmsStatus;
    }

    public void setRcmsStatus(String rcmsStatus) {
        this.rcmsStatus = rcmsStatus;
    }


    public Integer getTimesExecuted() {
        return timesExecuted;
    }

    public void setTimesExecuted(Integer timesExecuted) {
        this.timesExecuted = timesExecuted;
    }

    @Override
    public String toString() {
        return "RecoveryStepStatus{" +
                "stepIndex=" + stepIndex +
                ", started=" + started +
                ", finished=" + finished +
                ", status='" + status + '\'' +
                '}';
    }
}
