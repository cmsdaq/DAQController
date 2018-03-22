package ch.cern.cms.daq.expertcontroller;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;

@RestController
public class ExpertController {

    Logger logger = Logger.getLogger(ExpertController.class);

    @Autowired
    RecoveryManager recoveryManager;

    @RequestMapping(value = "/recover", method = RequestMethod.POST)
    public Long greeting(@RequestBody RecoveryRequest request) {

        logger.info("New recovery request: " + request.getProblemDescription());

        long requestId = recoveryManager.recover(request);
        return requestId;
    }

    @RequestMapping(value = "/status/{id}/", method = RequestMethod.GET)
    public String greeting(@PathVariable Long id) {

        logger.info("New recovery status request: " + id);

        String status = recoveryManager.getStatus(id);
        return status;
    }
}