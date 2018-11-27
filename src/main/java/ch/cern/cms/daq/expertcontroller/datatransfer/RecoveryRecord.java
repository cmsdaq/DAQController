package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class RecoveryRecord {

    String id;
    String name;
    String description;
    OffsetDateTime start;
    OffsetDateTime end;

}
