package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.websocket.ApprovalResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RecoveryServiceTest {

    RecoveryService recoveryService;

    @Before
    public void prepare(){
        recoveryService = new RecoveryService();
    }

    @Test(expected = IllegalArgumentException.class)
    public void noRecoveryIdArgumentTest(){

        ApprovalResponse approvalResponse = new ApprovalResponse();
        approvalResponse.setApproved(true);
        recoveryService.handleDecision(approvalResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void noRecoveryApprovedArgumentTest(){

        ApprovalResponse approvalResponse = new ApprovalResponse();
        approvalResponse.setRecoveryId(1L);
        recoveryService.handleDecision(approvalResponse);
    }

    @Test
    public void acceptRecoveryStep(){

        ApprovalResponse approvalResponse = new ApprovalResponse();
        approvalResponse.setRecoveryId(1L);
        approvalResponse.setStep(1);
        approvalResponse.setApproved(true);
        String result = recoveryService.handleDecision(approvalResponse);
        Assert.assertEquals("Recovery step successfully approved",result);
    }

    @Test
    public void accpetRecoveryProcedure(){

        ApprovalResponse approvalResponse = new ApprovalResponse();
        approvalResponse.setRecoveryId(1L);
        approvalResponse.setApproved(true);
        String result = recoveryService.handleDecision(approvalResponse);
        Assert.assertEquals("Recovery procedure successfully approved",result);
    }
}