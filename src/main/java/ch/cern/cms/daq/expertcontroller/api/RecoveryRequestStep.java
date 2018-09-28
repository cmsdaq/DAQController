package ch.cern.cms.daq.expertcontroller.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Fetch;

import javax.persistence.*;
import java.util.Date;
import java.util.Set;


/**
 * Part of recovery request that is sent from expert
 */
@Entity
@Table(name="recovery_request_step")
public class RecoveryRequestStep {

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    private Long id;

    /**
     * Step ordinal number in procedure
     */
    int stepIndex;

    @Transient
    String humanReadable;

    /**
     * Subsystems to red recycle
     */
    @CollectionTable(name="recovery_request_step_rrec",joinColumns = @JoinColumn(name="recovery_request_step_id"))
    @ElementCollection(fetch = FetchType.EAGER)
    Set<String> redRecycle;

    /**
     * Subsystems to green recycle
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name="recovery_request_step_grec", joinColumns = @JoinColumn(name="recovery_request_step_id"))
    Set<String> greenRecycle;

    /**
     * Subsystems to blame
     */
    @CollectionTable(name="recovery_request_step_fault",joinColumns = @JoinColumn(name="recovery_request_step_id"))
    @ElementCollection(fetch = FetchType.EAGER)
    Set<String> fault;

    /**
     * Subsystems to reset. Some schedules could have been planned by shifter. This will reset that actions.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name="recovery_request_step_reset",joinColumns = @JoinColumn(name="recovery_request_step_id"))
    Set<String> reset;

    @Transient
    @JsonIgnore
    String status;

    @Transient
    Date started;

    @Transient
    Date finished;

    Boolean issueTTCHardReset;


    /** How many times executed */
    private Integer timesExecuted;

    public Set<String> getGreenRecycle() {
        return greenRecycle;
    }

    public void setGreenRecycle(Set<String> greenRecycle) {
        this.greenRecycle = greenRecycle;
    }

    public Set<String> getFault() {
        return fault;
    }

    public void setFault(Set<String> fault) {
        this.fault = fault;
    }

    public Set<String> getReset() {
        return reset;
    }

    public void setReset(Set<String> reset) {
        this.reset = reset;
    }

    public Set<String> getRedRecycle() {
        return redRecycle;
    }

    public void setRedRecycle(Set<String> redRecycle) {
        this.redRecycle = redRecycle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getFinished() {
        return finished;
    }

    public void setFinished(Date finished) {
        this.finished = finished;
    }

    public Date getStarted() {
        return started;
    }

    public void setStarted(Date started) {
        this.started = started;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    public Integer getTimesExecuted() {
        return timesExecuted;
    }

    public void setTimesExecuted(Integer timesExecuted) {
        this.timesExecuted = timesExecuted;
    }

    public String getHumanReadable() {
        return humanReadable;
    }

    public void setHumanReadable(String humanReadable) {
        this.humanReadable = humanReadable;
    }

    public Boolean getIssueTTCHardReset() {
        return issueTTCHardReset;
    }

    public void setIssueTTCHardReset(Boolean issueTtcHardReset) {
        this.issueTTCHardReset = issueTtcHardReset;
    }

    @Override
    public String toString() {
        return "RecoveryRequestStep{" +
                "id=" + id +
                ", stepIndex=" + stepIndex +
                ", redRecycle=" + redRecycle +
                ", greenRecycle=" + greenRecycle +
                ", fault=" + fault +
                ", reset=" + reset +
                ", status='" + status + '\'' +
                ", started=" + started +
                ", finished=" + finished +
                ", timesExecuted=" + timesExecuted +
                '}';
    }
}
