package ch.cern.cms.daq.expertcontroller.persistence;

import javax.persistence.*;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
public class RecoveryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Long id;

    String name;

    String description;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "start_date")
    Date start;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "end_date")
    Date end;

    @Transient
    LinkedHashSet<Long> relatedConditions;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getStart() {
        return start;
    }

    public void setStart(Date start) {
        this.start = start;
    }

    public Date getEnd() {
        return end;
    }

    public void setEnd(Date end) {
        this.end = end;
    }

    public Long getId() {
        return id;
    }

    public LinkedHashSet<Long> getRelatedConditions() {
        return relatedConditions;
    }

    public void setRelatedConditions(LinkedHashSet<Long> relatedConditions) {
        this.relatedConditions = relatedConditions;
    }

    @Override
    public String toString() {
        return "RecoveryRecord{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", start=" + start +
                ", end=" + end +
                '}';
    }
}
