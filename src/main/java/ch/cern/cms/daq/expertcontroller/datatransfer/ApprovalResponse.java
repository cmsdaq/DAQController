package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.Data;

/**
 * POJO sent from dashboard in order to confirm approved steps to execute
 */
@Data
public class ApprovalResponse {

    Long recoveryId;

    Integer step;

    Boolean approved;

}
