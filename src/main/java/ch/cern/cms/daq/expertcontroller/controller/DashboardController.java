package ch.cern.cms.daq.expertcontroller.controller;

import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalRequest;
import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalResponse;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryStatusDTO;
import ch.cern.cms.daq.expertcontroller.service.RecoveryService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Controller of the dashboard client. Configured by WebSocketConfig
 *
 * @see WebSocketConfig
 */
@Controller
public class DashboardController {

    Logger logger = Logger.getLogger(DashboardController.class);


    @Autowired
    RecoveryService recoveryService;

    @Autowired
    private SimpMessagingTemplate template;

    /**
     * Called by the dashboard client on approval decision
     *
     * @param approvalResponse data transfer object describing what has been approved
     * @return status of the recovery that has been approved
     */
    @MessageMapping("/approve")
    @SendTo("/topic/recovery-status")
    public RecoveryStatusDTO approve(@RequestBody ApprovalResponse approvalResponse) {

        logger.info("Approval request: " + approvalResponse);
        recoveryService.handleDecision(approvalResponse);

        return recoveryService.getStatus();

    }

    /**
     * Sends approval request to the dashboard client
     *
     * @param approvalRequest data transfer object describing what needs to be approved
     */
    public void requestApprove(ApprovalRequest approvalRequest) {
        logger.info("Requesting operator approval of recovery: " + approvalRequest.getRecoveryId());
        this.template.convertAndSend("/topic/approveRequests", approvalRequest);
    }


    public void notifyTimeout(long id) {
        this.template.convertAndSend("/topic/timeout", id);
    }


    /**
     * Sends current recovery status to the dashboard client
     *
     * @param recoveryStatusDTO data transfer object describing status of current recovery
     */
    public void notifyRecoveryStatus(RecoveryStatusDTO recoveryStatusDTO) {
        logger.info("Notifying dashboard status changed: " + recoveryStatusDTO);
        this.template.convertAndSend("/topic/recovery-status", recoveryStatusDTO);
    }

}