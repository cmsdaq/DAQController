package ch.cern.cms.daq.expertcontroller.websocket;

import ch.cern.cms.daq.expertcontroller.RecoveryManager;
import ch.cern.cms.daq.expertcontroller.RecoveryRequest;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GreetingController {

    Logger logger = Logger.getLogger(GreetingController.class);


    @Autowired
    RecoveryManager recoveryManager;

    @Autowired
    private SimpMessagingTemplate template;

    @MessageMapping("/approve")
    @SendTo("/topic/greetings")
    public String approve(@RequestBody Approval approval) throws Exception {

        int id = approval.getId();
        if (approval.isApproved()) {

            logger.info("Operator approved recovery " + id);
            Thread.sleep(1000); // simulated delay
            recoveryManager.approved(id);
            return "Successfully approved";
        } else {

            logger.info("Operator rejected recovery " + id);
            recoveryManager.rejected(id);
            return "Successfully rejected";
        }
    }

    public void requestApprove(RecoveryRequest recoveryRequest) {
        System.out.println("Requesting operator approval of recovery: " + recoveryRequest.getId());
        this.template.convertAndSend("/topic/greetings", recoveryRequest);
    }

    public void notifyTimeout(long id){
        this.template.convertAndSend("/topic/timeout", id);
    }

}