package ch.cern.cms.daq.expertcontroller.entity;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import javax.persistence.*;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;


/**
 * Recovery job that will be executed by controller.
 */
@Builder
@ToString
@Data
@Entity
public class RecoveryProcedure {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    /**
     * Related problem ids
     */
    @ElementCollection
    @CollectionTable(name ="problemIds")
    private List<Long> problemIds;

    private String problemTitle;

    private ZonedDateTime start;

    private ZonedDateTime end;

    private String state;

    /**
     * Jobs that were executed. Detailed information when and how many times there were executed.
     */
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name="recovery_procedure_id")
    @OrderColumn(name="list_index")
    private List<RecoveryJob> executedJobs;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name="recovery_procedure_id")
    @OrderColumn(name="list_index")
    private List<Event> eventSummary;


    /**
     * Procedure that was created based on first request
     */
    @Transient
    private final List<RecoveryJob> procedure;

    @Transient
    private Iterator<RecoveryJob> iterator;

    @Transient
    private RecoveryJob nextStep;

    //TODO: is it good place for this?
    public RecoveryJob getNextJob() {
        if (procedure == null) {
            throw new IllegalStateException("Recovery procedure has no steps defined");
        }

        if (iterator == null) {
            iterator = procedure.iterator();
        }
        if (iterator.hasNext()) {
            return iterator.next();
        } else {
            return null;
        }
    }
}
