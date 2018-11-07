package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class RecoveryRecord {

    Long id;
    String name;
    String description;
    Date start;
    Date end;

}
