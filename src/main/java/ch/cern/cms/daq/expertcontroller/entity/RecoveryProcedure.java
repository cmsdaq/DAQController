package ch.cern.cms.daq.expertcontroller.entity;

import ch.cern.cms.daq.expertcontroller.service.recoveryservice.ExecutionMode;
import lombok.*;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;


/**
 * Recovery job that will be executed by controller.
 */
@Builder
@ToString
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryProcedure {

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    private Long id;

    /**
     * Related problem ids
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "problemIds")
    private List<Long> problemIds;

    private String problemTitle;

    @Column(name = "start_date")
    private OffsetDateTime start;

    @Column(name = "end_date")
    private OffsetDateTime end;

    private String state;

    /**
     * Jobs that were executed. Detailed information when and how many times there were executed.
     */
    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "recovery_procedure_id")
    @OrderColumn(name = "list_index")
    private List<RecoveryJob> executedJobs;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "recovery_procedure_id")
    @OrderColumn(name = "list_index")
    private List<RecoveryEvent> eventSummary;

    private Boolean isProbe;

    /**
     * Procedure that was created based on first request
     */
    @Transient
    private List<RecoveryJob> procedure;

    @Transient
    private Iterator<RecoveryJob> iterator;

    @Transient
    private RecoveryJob nextStep;


    //TODO: enable persistence for that at some point
    @Transient
    private ExecutionMode executionMode;


    public RecoveryJob outOfSequenceJob(int index){

        Iterator<RecoveryJob> i = procedure.iterator();

        while(i.hasNext()){
            RecoveryJob current = i.next();
            if(current.getStepIndex() == index){
                iterator = i;
                return current;
            }
        }
        return null;

    }

    //TODO: is it good place for this?
    public RecoveryJob getNextJob() {
        if (procedure == null) {
            return null;
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
