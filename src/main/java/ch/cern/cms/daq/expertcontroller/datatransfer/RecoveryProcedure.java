package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class RecoveryProcedure {

    private Long id;

    private List<Long> problemIds;

    private String problemTitle;

    private OffsetDateTime start;

    private OffsetDateTime end;

    private String state;

    private List<RecoveryJob> executedJobs;

    private List<Event> eventSummary;

}
