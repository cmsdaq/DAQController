package ch.cern.cms.daq.expertcontroller.websocket;

import java.util.HashMap;
import java.util.List;

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
