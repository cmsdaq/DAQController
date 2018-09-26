package ch.cern.cms.daq.expertcontroller.websocket;

import java.util.HashMap;
import java.util.List;

/**
 * Request sent from controller to dashboard in order to ask for approval of given recovery.
 */
public class ApprovalRequest {


    private long recoveryId;

    public ApprovalRequest() {
    }

    public ApprovalRequest(long recoveryId) {
        this.recoveryId = recoveryId;
    }



    public long getRecoveryId() {
        return recoveryId;
    }

    public void setRecoveryId(long recoveryId) {
        this.recoveryId = recoveryId;
    }

}
