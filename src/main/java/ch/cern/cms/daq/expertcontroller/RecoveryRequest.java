package ch.cern.cms.daq.expertcontroller;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
public class RecoveryRequest {


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Long id;

    /**
     * Description of the problem that will be recovered
     */
    String problemDescription;

    /**
     * Steps that cannot be automatized and must be done manually
     */
    @Transient
    Set<String> manualSteps;


    /**
     * Subsystems to red recycle
     */
    @Transient
    Set<String> redRecycle;

    /**
     * Subsystems to green recycle
     */
    @Transient
    Set<String> greenRecycle;

    /**
     * Subsystems to blame
     */
    @Transient
    Set<String> fault;

    /**

     * Subsystems to reset. Some schedules could have been planned by shifter. This will reset that actions.
     */
    @Transient
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

    public String getProblemDescription() {
        return problemDescription;
    }

    public void setProblemDescription(String problemDescription) {
        this.problemDescription = problemDescription;
    }

    public Set<String> getManualSteps() {
        return manualSteps;
    }

    public void setManualSteps(Set<String> manualSteps) {
        this.manualSteps = manualSteps;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
