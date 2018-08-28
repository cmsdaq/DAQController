package ch.cern.cms.daq.expertcontroller.websocket;

import java.util.List;
import java.util.Date;

/**
 * Status of the recovery. This POJO is used to communicate current state of recovery to the dashboard.
 */
public class RecoveryStatus {

    /** Id of the recovery, equal to id of the main recovery entry.
    Identifies the recovery as understood by controller (as oppose to problem id - given by expert, and recovery request
    id - given by controller on every request from expert, including subsequent request to the same problem)  */
    Long id;

    /**
     * Related condition ids.
     */
    List<Long> conditionIds;

    /**
     * Related request ids
     */
    List<Long> requestIds;

    String status;

    List<RecoveryStepStatus> automatedSteps;

    /** Date when recovery was started */
    Date startDate;

    /** Date when recovery was finished */
    Date endDate;


    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<RecoveryStepStatus> getAutomatedSteps() {
        return automatedSteps;
    }

    public void setAutomatedSteps(List<RecoveryStepStatus> automatedSteps) {
        this.automatedSteps = automatedSteps;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<Long> getConditionIds() {
        return conditionIds;
    }

    public void setConditionIds(List<Long> conditionIds) {
        this.conditionIds = conditionIds;
    }

    public List<Long> getRequestIds() {
        return requestIds;
    }

    public void setRequestIds(List<Long> requestIds) {
        this.requestIds = requestIds;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    @Override
    public String toString() {
        return "RecoveryStatus{" +
                "id=" + id +
                ", conditionIds=" + conditionIds +
                ", requestIds=" + requestIds +
                ", status='" + status + '\'' +
                ", automatedSteps=" + automatedSteps +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}
