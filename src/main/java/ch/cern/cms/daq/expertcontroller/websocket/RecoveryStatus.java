package ch.cern.cms.daq.expertcontroller.websocket;

import java.util.List;
import java.util.Date;

public class RecoveryStatus {

    Long id;

    List<Long> conditionIds;

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
                ", status='" + status + '\'' +
                ", automatedSteps=" + automatedSteps +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}
