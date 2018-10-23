package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.Data;

import java.util.Date;

@Data
public class RecoveryRecordDTO {

    Long id;
    String name;
    String description;
    Date start;
    Date end;

}
