package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POJO sent from dashboard in order to confirm approved steps to execute
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApprovalResponse {

    Long recoveryId;

    Integer step;

    Boolean approved;

}
