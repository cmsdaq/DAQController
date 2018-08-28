package ch.cern.cms.daq.expertcontroller.api;



import javax.persistence.*;
import java.util.List;
import java.util.Date;

/**
 * POJO sent from DAQExpert in order to request a recovery.
 *
 */
@Entity
public class RecoveryRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    private Long id;

    /**
     * Id of the condition in DAQExpert. Used to match automatic recovery with current problem
     */
    private Long problemId;


    /**
     * Status of the recovery request
     */
    private String status;


    /**
     * Used to indicate that this request should preempt current recovery
     */
    private boolean withInterrupt;

    /**
     * Used to indicate that this request should continue current recovery
     */
    private boolean isSameProblem;

    /**
     * Used to indicate that this request is less important that current one and should be postponed
     */
    private boolean withPostponement;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name="recovery_request_id")
    private List<RecoveryRequestStep> recoverySteps;

    String problemTitle;

    /**
     * Description of the problem that will be recovered
     */
    String problemDescription;


    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "received")
    Date received;

    public String getProblemDescription() {
        return problemDescription;
    }

    public void setProblemDescription(String problemDescription) {
        this.problemDescription = problemDescription;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProblemId() {
        return problemId;
    }

    public void setProblemId(Long problemId) {
        this.problemId = problemId;
    }

    public boolean isWithInterrupt() {
        return withInterrupt;
    }

    public void setWithInterrupt(boolean withInterrupt) {
        this.withInterrupt = withInterrupt;
    }


    public List<RecoveryRequestStep> getRecoverySteps() {
        return recoverySteps;
    }

    public void setRecoverySteps(List<RecoveryRequestStep> recoveryRequestSteps) {
        this.recoverySteps = recoveryRequestSteps;
    }


    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSameProblem() {
        return isSameProblem;
    }

    public void setSameProblem(boolean sameProblem) {
        isSameProblem = sameProblem;
    }

    public boolean isWithPostponement() {
        return withPostponement;
    }

    public void setWithPostponement(boolean withPostponement) {
        this.withPostponement = withPostponement;
    }

    public String getProblemTitle() {
        return problemTitle;
    }

    public void setProblemTitle(String problemTitle) {
        this.problemTitle = problemTitle;
    }

    public Date getReceived() {
        return received;
    }

    public void setReceived(Date received) {
        this.received = received;
    }

    @Override
    public String toString() {
        return "RecoveryRequest{" +
                "id=" + id +
                ", problemId=" + problemId +
                ", status='" + status + '\'' +
                ", withInterrupt=" + withInterrupt +
                ", isSameProblem=" + isSameProblem +
                ", withPostponement=" + withPostponement +
                ", recoverySteps=" + recoverySteps +
                ", problemTitle='" + problemTitle + '\'' +
                ", problemDescription='" + problemDescription + '\'' +
                '}';
    }
}
