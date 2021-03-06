package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalResponse;
import ch.cern.cms.daq.expertcontroller.service.IRecoveryService;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
//TODO: get inspiration from this tests
public class RecoveryServiceTest {

    IRecoveryService recoveryService;

    @Before
    public void prepare() {
        //recoveryService = new RecoveryService();
    }

    @Test(expected = IllegalArgumentException.class)
    public void noRecoveryIdArgumentTest() {

        ApprovalResponse approvalResponse = new ApprovalResponse();
        approvalResponse.setApproved(true);
        //recoveryService.handleDecision(approvalResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void noRecoveryApprovedArgumentTest() {

        ApprovalResponse approvalResponse = new ApprovalResponse();
        approvalResponse.setRecoveryProcedureId(1L);
        //recoveryService.handleDecision(approvalResponse);
    }

    @Test
    public void acceptRecoveryStep() {

        ApprovalResponse approvalResponse = new ApprovalResponse();
        approvalResponse.setRecoveryProcedureId(1L);
        approvalResponse.setStep(1);
        approvalResponse.setApproved(true);
        //String result = recoveryService.handleDecision(approvalResponse);
        //Assert.assertEquals("Recovery step successfully approved", result);
    }

    @Test
    public void accpetRecoveryProcedure() {

        ApprovalResponse approvalResponse = new ApprovalResponse();
        approvalResponse.setRecoveryProcedureId(1L);
        approvalResponse.setApproved(true);
       //String result = recoveryService.handleDecision(approvalResponse);
        //Assert.assertEquals("Recovery procedure successfully approved", result);
    }
}