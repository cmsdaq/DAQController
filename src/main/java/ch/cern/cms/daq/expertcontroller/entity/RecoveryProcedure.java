package ch.cern.cms.daq.expertcontroller.entity;

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
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    /**
     * Related problem ids
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "problemIds")
    private List<Long> problemIds;

    private String problemTitle;

    private OffsetDateTime start;

    private OffsetDateTime end;

    private String state;

    /**
     * Jobs that were executed. Detailed information when and how many times there were executed.
     */
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "recovery_procedure_id")
    @OrderColumn(name = "list_index")
    private List<RecoveryJob> executedJobs;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
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
