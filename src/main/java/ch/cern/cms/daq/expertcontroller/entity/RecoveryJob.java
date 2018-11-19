package ch.cern.cms.daq.expertcontroller.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Defines recovery actions. All recoveries must be schedule at once. I.e. Cannot have both RecoveryStarts and stop and reset.
 * This have to be defined as 2 steps in 1 Recovery Job.
 *
 * @see RecoveryProcedure
 */
@Data
@Builder
@ToString
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryJob {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    /**
     * Placeholder job
     */
    String job;

    OffsetDateTime start;

    OffsetDateTime end;

    Boolean issueTTCHardReset;

    Integer stepIndex;

    @ElementCollection
    @CollectionTable(name ="recoveryJobRedRecycle")
    Set<String> redRecycle;

    @ElementCollection
    @CollectionTable(name ="recoveryJobGreenRecycle")
    Set<String> greenRecycle;

    @ElementCollection
    @CollectionTable(name ="recoveryJobFault")
    Set<String> fault;

    @ElementCollection
    @CollectionTable(name ="recoveryJobReset")
    Set<String> reset;

    /*TODO: remove this: job can be executed only once (step will count jobs with given stepIndex)*/
    Integer executionCount;

    String status;

    @JsonIgnore
    @Transient
    Long procedureId;

    public String toCompactString(){
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName());
        sb.append("(");
        sb.append("id=").append(id);
        sb.append(", ").append("stepIndex=").append(stepIndex);
        if(job != null){
            sb.append(", ").append("job=").append(job);
        }
        if(issueTTCHardReset!= null && issueTTCHardReset){
            sb.append(", ").append("issueTTCHardReset=").append(issueTTCHardReset);
        }
        if(redRecycle != null && redRecycle.size() > 0){
            sb.append(", ").append("redRecycle=").append(redRecycle);
        }
        if(greenRecycle != null && greenRecycle.size() > 0){
            sb.append(", ").append("greenRecycle=").append(greenRecycle);
        }
        if(fault != null && fault.size() > 0){
            sb.append(", ").append("fault=").append(fault);
        }
        if(reset != null && reset.size() > 0){
            sb.append(", ").append("reset=").append(reset);
        }
        if(start != null){
            sb.append(", ").append("start=").append(start);
        }
        if(end != null){
            sb.append(", ").append("end=").append(end);
        }
        if(executionCount != null){
            sb.append(" ,").append("executionCount=").append(executionCount);
        }

        sb.append(")");
        return sb.toString();
    }

}
