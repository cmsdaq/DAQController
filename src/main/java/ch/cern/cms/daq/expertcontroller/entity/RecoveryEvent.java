package ch.cern.cms.daq.expertcontroller.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.OffsetDateTime;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    private Long id;

    @Column(name = "event_date")
    private OffsetDateTime date;

    private String type;

    private String content;

    /**
     * Index of step this event in context of
     */
    private Integer stepIndex;
}
