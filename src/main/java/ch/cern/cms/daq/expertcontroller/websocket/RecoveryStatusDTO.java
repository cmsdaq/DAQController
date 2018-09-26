package ch.cern.cms.daq.expertcontroller.websocket;

import lombok.Data;

import java.util.List;
import java.util.Date;

/**
 * Status of the recovery. This POJO is used to communicate current state of recovery to the dashboard.
 */
@Data
public class RecoveryStatusDTO {

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

    List<RecoveryStepStatusDTO> automatedSteps;

    /** Date when recovery was started */
    Date startDate;

    /** Date when recovery was finished */
    Date endDate;

    @Override
    public String toString() {
        return "RecoveryStatusDTO{" +
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
