package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Event {

    private Long id;

    private OffsetDateTime date;

    private String type;

    private String content;
}
