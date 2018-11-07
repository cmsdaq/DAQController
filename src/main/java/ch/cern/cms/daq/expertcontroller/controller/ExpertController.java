package ch.cern.cms.daq.expertcontroller.controller;

import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryProcedureStatus;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRecord;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryResponse;
import ch.cern.cms.daq.expertcontroller.service.IRecoveryService;
import ch.cern.cms.daq.expertcontroller.service.ProbeRecoverySender;
import ch.cern.cms.daq.expertcontroller.service.RecoveryRecordService;
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
import java.util.List;


/**
 * Main application controller allowing communication with other services
 */
@RestController
public class ExpertController {

    Logger logger = Logger.getLogger(ExpertController.class);

    @Autowired
    IRecoveryService recoveryService;

    @Autowired
    RecoveryRecordService recoveryRecordService;

    @Autowired
    ProbeRecoverySender probeRecoverySender;

    @Value("${controller.message}")
    private String message;

    /**
     * Defines endpoint to request recovery
     *
     * @param request data transfer object describing recovery request
     * @return response to the recovery request, includes decision whether recovery request has been accepted or
     * rejected
     */
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
        //recoveryService.finished(id);
    }

    /**
     * Endpoint to schedule test recovery
     *
     * @param subsystem subsystem to which test recovery will be applied
     * @return confirmation message
     */
    @RequestMapping(value = "/fire-test-recovery", method = RequestMethod.GET)
    public String testRecovery(@RequestParam(value = "subsystem", required = false) String subsystem) {
        logger.info("Issuing test recovery sequence");
        probeRecoverySender.issueTestRecoverySequence(subsystem);
        return "Probe test recovery sequence completed";
    }


    /**
     * Endpoint to get acceptanceDecision of a given recovery. Id corresponds to Recovery record.
     *
     * @param id id of the recovery record
     * @returns data transfer object describing acceptanceDecision of given recovery
     */
    @RequestMapping(value = "/acceptanceDecision/{id}/", method = RequestMethod.GET)
    public ResponseEntity<RecoveryProcedureStatus> status(@PathVariable Long id) {
        throw new NotYetImplementedException();
    }

    /**
     * Endpoint to get acceptanceDecision of a given recovery.
     *
     * @returns data transfer object describing acceptanceDecision of current recovery
     */
    @RequestMapping(value = "/acceptanceDecision/", method = RequestMethod.GET)
    public ResponseEntity<RecoveryProcedureStatus> status() {

        logger.info("New recovery acceptanceDecision request");

        RecoveryProcedureStatus recoveryProcedureStatus = null;//recoveryService.getAcceptanceDecision();

        if (recoveryProcedureStatus != null) {

            return ResponseEntity.ok(recoveryProcedureStatus);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }


    @Deprecated
    @RequestMapping(value = "/acceptanceDecision/{id}/{step}/", method = RequestMethod.GET)
    public String status(@PathVariable Long id, @PathVariable Integer step) {

        logger.info("New recovery acceptanceDecision request: " + id + ":" + step);

        String status = null;// recoveryService.getAcceptanceDecision(id, step);
        return status;
    }

    /**
     * Endpoint to retrieve recovery records within given time span
     *
     * @param start beginning of requested time span
     * @param end   end of requested time span
     * @return data transfer objects describing recovery records within given time span
     */
    @CrossOrigin(origins = "*")
    @RequestMapping(value = "/records")
    public Collection<RecoveryRecord> getRecoveryRecords(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end) {
        logger.info("Requested records between: " + start + " and  " + end);
        List<RecoveryRecord> result = recoveryRecordService.getRecords(start, end);

        logger.debug("Result: " + result);
        return result;
    }

    /**
     * Endpont to get current instance description
     *
     * @return current instance description
     */
    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String main() {
        return "Controller up and running: " + message;
    }

}