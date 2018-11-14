package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Set;

@Data
public class RecoveryJob {

    private Long id;

    String job;

    OffsetDateTime start;

    OffsetDateTime end;

    Boolean issueTTCHardReset;

    Integer stepIndex;

    Set<String> redRecycle;

    Set<String> greenRecycle;

    Set<String> fault;

    Set<String> reset;

    Integer executionCount;
}
