package ch.cern.cms.daq.expertcontroller.websocket;

/**
 * POJO sent from dashboard in order to confirm approved steps to execute
 */
public class ApprovalResponse {

    Long recoveryId;

    Integer step;

    Boolean approved;

    public Long getRecoveryId() {
        return recoveryId;
    }

    public void setRecoveryId(Long recoveryId) {
        this.recoveryId = recoveryId;
    }

    public Boolean isApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public Integer getStep() {
        return step;
    }

    public void setStep(Integer step) {
        this.step = step;
    }

    @Override
    public String toString() {
        return "ApprovalResponse{" +
                "recoveryId=" + recoveryId +
                ", step=" + step +
                ", approved=" + approved +
                '}';
    }
}
