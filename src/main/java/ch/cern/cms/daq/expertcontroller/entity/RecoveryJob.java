package ch.cern.cms.daq.expertcontroller.entity;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import javax.persistence.*;
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
public class RecoveryJob {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    /**
     * Placeholder job
     */
    String job;

    ZonedDateTime start;

    ZonedDateTime end;

    Boolean issueTTCHardReset;

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

    Integer executionCount;

}
