package ch.cern.cms.daq.expertcontroller.websocket;

import ch.cern.cms.daq.expertcontroller.RecoveryService;
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
    RecoveryService recoveryService;

    @Autowired
    private SimpMessagingTemplate template;

    /**
     * Method called by
     * @param approvalResponse
     * @return
     */
    @MessageMapping("/approve")
    @SendTo("/topic/recovery-status")
    public RecoveryStatusDTO approve(@RequestBody ApprovalResponse approvalResponse) {

        logger.info("Approval request: " + approvalResponse);
        recoveryService.handleDecision(approvalResponse);

        return recoveryService.getStatus();

    }

    public void requestApprove(ApprovalRequest approvalRequest) {
        logger.info("Requesting operator approval of recovery: " + approvalRequest.getRecoveryId());
        this.template.convertAndSend("/topic/approveRequests", approvalRequest);
    }

    public void notifyTimeout(long id){
        this.template.convertAndSend("/topic/timeout", id);
    }


    public void notifyRecoveryStatus(RecoveryStatusDTO recoveryStatusDTO){
        logger.info("Notifying dashboard status changed: " + recoveryStatusDTO);
        this.template.convertAndSend("/topic/recovery-status", recoveryStatusDTO);
    }

}