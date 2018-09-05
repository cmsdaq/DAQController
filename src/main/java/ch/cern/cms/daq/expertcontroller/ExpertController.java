package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.api.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.api.RecoveryResponse;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecord;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecordRepository;
import ch.cern.cms.daq.expertcontroller.websocket.RecoveryStatus;
import org.apache.log4j.Logger;
import org.hibernate.cfg.NotYetImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;


@RestController
public class ExpertController {

    Logger logger = Logger.getLogger(ExpertController.class);

    @Autowired
    RecoveryService recoveryService;

    @Autowired
    RecoveryRecordRepository recoveryRecordRepository;

    @Autowired
    ProbeRecoverySender probeRecoverySender;

    @Value("${controller.message}")
    private String message;

    @RequestMapping(value = "/recover", method = RequestMethod.POST)
    public ResponseEntity<RecoveryResponse> requestRecovery(@RequestBody RecoveryRequest request) {

        logger.info("New recovery request: " + request.getProblemDescription() + " problem id: " + request.getProblemId());

        if (request.getRecoverySteps() == null || request.getRecoverySteps().size() == 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null); //TODO might be useful to describe why bad request
        }
        RecoveryResponse response = recoveryService.submitRecoveryRequest(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    /**
     * Endpoint to indicate that given condition finished.
     *
     * @param id identification number of condition given by the expert system. Note that it must be the same as the one
     *           used while issuing recovery.
     */
    @RequestMapping(value = "/finished", method = RequestMethod.POST)
    public void conditionFinished(@RequestBody Long id) {
        logger.info("Finished signal received: " + id);
        recoveryService.finished(id);
    }

    @RequestMapping(value = "/fire-test-recovery", method = RequestMethod.GET)
    public String testRecovery() {
        logger.info("Issuing test recovery sequence");
        probeRecoverySender.issueTestRecoverySequence();
        return "Probe test recovery sequence completed";
    }


    /**
     * Status of recovery. Id corresponds to Recovery record. TODO: make it possible to use this API with and without
     */
    @RequestMapping(value = "/status/{id}/", method = RequestMethod.GET)
    public ResponseEntity<RecoveryStatus> status(@PathVariable Long id) {
        throw new NotYetImplementedException();
    }

    /**
     * Status of current recovery.
     */
    @RequestMapping(value = "/status/", method = RequestMethod.GET)
    public ResponseEntity<RecoveryStatus> status() {

        logger.info("New recovery status request");

        RecoveryStatus recoveryStatus = recoveryService.getStatus();

        if (recoveryStatus != null) {

            return ResponseEntity.ok(recoveryStatus);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @Deprecated
    @RequestMapping(value = "/status/{id}/{step}/", method = RequestMethod.GET)
    public String status(@PathVariable Long id, @PathVariable Integer step) {

        logger.info("New recovery status request: " + id + ":" + step);

        String status = recoveryService.getStatus(id, step);
        return status;
    }

    @CrossOrigin(origins = "*")
    @RequestMapping(value = "/records")
    public Collection<RecoveryRecord> getRecoveryRecords(@RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start, @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end) {
        logger.info("Requested records between: " + start + " and  " + end);
        List<RecoveryRecord> result = recoveryRecordRepository.findBetween(Date.from(start.toInstant()), Date.from(end.toInstant()));
        logger.debug("Result: " + result);
        return result;
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String main() {
        return "Controller up and running: " + message;
    }

}