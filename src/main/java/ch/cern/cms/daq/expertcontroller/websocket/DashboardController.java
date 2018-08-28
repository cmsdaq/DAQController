package ch.cern.cms.daq.expertcontroller.websocket;

import ch.cern.cms.daq.expertcontroller.RecoveryManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class DashboardController {

    Logger logger = Logger.getLogger(DashboardController.class);


    @Autowired
    RecoveryManager recoveryManager;

    @Autowired
    private SimpMessagingTemplate template;

    @MessageMapping("/approve")
    @SendTo("/topic/recovery-status")
    public RecoveryStatus approve(@RequestBody ApprovalResponse approvalResponse) {

        logger.info("Approval request: " + approvalResponse);
        recoveryManager.handleDecision(approvalResponse);

        return recoveryManager.getStatus();

    }

    public void requestApprove(ApprovalRequest approvalRequest) {
        System.out.println("Requesting operator approval of recovery: " + approvalRequest.getRecoveryId());
        this.template.convertAndSend("/topic/approveRequests", approvalRequest);
    }

    public void notifyTimeout(long id){
        this.template.convertAndSend("/topic/timeout", id);
    }


    public void notifyRecoveryStatus(RecoveryStatus recoveryStatus){
        logger.info("Notifying dashboard status changed: " + recoveryStatus);
        this.template.convertAndSend("/topic/recovery-status", recoveryStatus);
    }

}