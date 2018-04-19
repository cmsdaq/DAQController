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
public class RecoveryRequestStep {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    /**
     * Step ordinal number in procedure
     */
    int stepIndex;

    /**
     * Subsystems to red recycle
     */
    @ElementCollection(fetch = FetchType.EAGER)
    Set<String> redRecycle;

    /**
     * Subsystems to green recycle
     */
    @ElementCollection(fetch = FetchType.EAGER)
    Set<String> greenRecycle;

    /**
     * Subsystems to blame
     */
    @ElementCollection(fetch = FetchType.EAGER)
    Set<String> fault;

    /**
     * Subsystems to reset. Some schedules could have been planned by shifter. This will reset that actions.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    Set<String> reset;

    @JsonIgnore
    String status;

    Date started;

    Date finished;

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


    @Override
    public String toString() {
        return "RecoveryRequest{" +
                "redRecycle=" + redRecycle +
                ", greenRecycle=" + greenRecycle +
                ", fault=" + fault +
                ", reset=" + reset +
                '}';
    }
}
