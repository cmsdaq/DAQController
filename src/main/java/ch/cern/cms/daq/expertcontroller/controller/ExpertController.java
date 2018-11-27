package ch.cern.cms.daq.expertcontroller.controller;

import ch.cern.cms.daq.expertcontroller.datatransfer.*;
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

import java.time.OffsetDateTime;
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

        logger.info("New recovery request for problem: " + request.getProblemId() + " " + request.getProblemTitle());

        try {
            RecoveryResponse response = recoveryService.submitRecoveryRequest(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {

            logger.warn("Bad request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null); //TODO might be useful to describe why bad request

        }


    }

    @RequestMapping(value = "/service-status", method = RequestMethod.GET)
    public RecoveryServiceStatus getRecoveryServiceStatus() {
        logger.info("Service status request");
        return recoveryService.getRecoveryServiceStatus();
    }

    @RequestMapping(value = "/procedure-status/{id}", method = RequestMethod.GET)
    public RecoveryProcedureStatus getRecoveryProcedureStatus(@PathVariable Long id) {
        logger.info("Procedure status request: " + id);
        return recoveryService.getRecoveryProcedureStatus(id);
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

    /**
     * Endpoint to schedule test recovery
     *
     * @return confirmation message
     */
    @RequestMapping(value = "/fire-ttchr", method = RequestMethod.POST)
    public ResponseEntity<RecoveryResponse> testTTCHR() {
        logger.info("Issuing test TTC hard reset");
        return probeRecoverySender.issueTTCHardReset();
    }

    /**
     * Endpoint to schedule test recovery
     *
     * @param subsystem subsystem to which test recovery will be applied
     * @return confirmation message
     */
    @RequestMapping(value = "/fire-recovery", method = RequestMethod.POST)
    public ResponseEntity<RecoveryResponse> testRecovery(@RequestBody String subsystem) {
        logger.info("Issuing test recovery");
        return probeRecoverySender.issueRecovery(subsystem);
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
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        logger.info("Requested records between: " + start + " and  " + end);
        List<RecoveryRecord> result = recoveryRecordService.getRecords(start, end);

        logger.debug("Result: " + result);
        return result;
    }

    @RequestMapping(value = "/interrupt", method = RequestMethod.GET)
    public InterruptResponse interrupt() {
        return recoveryService.interrupt();
    }

    @RequestMapping(value = "/procedures", method = RequestMethod.GET)
    public List<ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryProcedure> getProcedures(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        return recoveryRecordService.getProcedures(start, end);
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

    /**
     * Endpont to get current automation status
     *
     * @return current automation status
     */
    @RequestMapping(value = "/automation", method = RequestMethod.GET)
    public String getAutomationMode() {
        return recoveryService.automationStatus();
    }

    /**
     * Endpont to get current automation status
     *
     * @return current automation status
     */
    @RequestMapping(value = "/automation", method = RequestMethod.POST)
    public String setAutomationMode(@RequestBody boolean enabled) {
        logger.info("Request to change automatio mode: " + enabled);
        return recoveryService.enableAutomation(enabled);
    }


}