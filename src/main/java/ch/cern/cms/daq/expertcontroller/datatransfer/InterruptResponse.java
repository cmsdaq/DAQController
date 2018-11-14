package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterruptResponse {

    String status;

    String message;

}

