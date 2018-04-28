package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.api.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.api.RecoveryRequestStep;
import ch.cern.cms.daq.expertcontroller.api.RecoveryResponse;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryJobRepository;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecord;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecordRepository;
import ch.cern.cms.daq.expertcontroller.rcmsController.LV0AutomatorControlException;
import ch.cern.cms.daq.expertcontroller.rcmsController.RcmsController;
import ch.cern.cms.daq.expertcontroller.websocket.ApprovalRequest;
import ch.cern.cms.daq.expertcontroller.websocket.ApprovalResponse;
import ch.cern.cms.daq.expertcontroller.websocket.DashboardController;
import ch.cern.cms.daq.expertcontroller.websocket.RecoveryStepStatus;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;


/**
 * Responsible for managing the whole recovery process
 */
@Component("recoveryManager")
public class RecoveryManager {

    private final static Logger logger = Logger.getLogger(RecoveryManager.class);
    /**
     * Thread executor for handling jobs
     */
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    @Autowired
    private RcmsController rcmsController;


    @Autowired
    private RecoveryJobRepository recoveryJobRepository;

    @Autowired
    private RecoveryRecordRepository recoveryRecordRepository;

    @Autowired
    private RecoverySequenceController recoverySequenceController;

    @Autowired
    private DashboardController dashboardController;

    /**
     * Currently executed request.
     */
    private RecoveryRequest currentRequest;

    /**
     * Last request. Used when currentlRequest has completed but there is a status request to build.
     */
    private RecoveryRequest lastRequest;

    /**
     * Less important request in comparison to currentRequest. It will be executed if and only if currentRequest was
     * fully processed and condition that generated waitingRequest has not been finished
     */
    private RecoveryRequest waitingRequest;


    /**
     * Submit new recovery request. This method will handle everything from filtering the request, requesting operator
     * approval to performing the recovery and persisting the status
     */
    public RecoveryResponse submitRecoveryRequest(RecoveryRequest request) {

        RecoveryResponse response = new RecoveryResponse();
        String status = acceptRecoveryRequest(request);

        response.setStatus(status);
        request.setReceived(new Date());


        logger.info("Request will be " + status);

        //TODO: we don't want strings here, replace it with enums
        switch (status) {
            case "accepted":
                request.setStatus("awaiting approval");
                recoveryJobRepository.save(request);
                recoverySequenceController.start(request);

                setupRecoveryRequest(request);
                break;
            case "acceptedWithPreemption":
                currentRequest.setStatus("preempted");
                request.setStatus("awaiting approval");
                recoveryJobRepository.save(currentRequest);
                recoveryJobRepository.save(request);

                rcmsController.interrupt();

                waitingRequest = currentRequest;
                recoverySequenceController.preempt(request);
                setupRecoveryRequest(request);
                break;

            case "acceptedToContinue":

                request.setStatus("acceptedToContinue");
                response.setContinuesTheConditionId(currentRequest.getProblemId());

                getStepExecutionCount(currentRequest, request);
                recoveryJobRepository.save(request);
                recoverySequenceController.continueSame(request);


                setupRecoveryRequest(request); // pass current request
                break;
            case "acceptedToPostpone":

                request.setStatus("acceptedToPostpone");
                recoveryJobRepository.save(request);
                waitingRequest = request;

                break;
            case "rejected":
                request.setStatus("rejected");
                recoveryJobRepository.save(request);
                response.setRejectedDueToConditionId(currentRequest.getProblemId());
                break;
            default:
                break;
        }

        response.setRecoveryId(request.getId());
        return response;

    }

    private void getStepExecutionCount(RecoveryRequest previousRequest, RecoveryRequest nextRequest) {
        for (RecoveryRequestStep step : previousRequest.getRecoverySteps()) {
            RecoveryRequestStep correspondingStep = nextRequest.getRecoverySteps().stream().filter(s -> s.getStepIndex() == step.getStepIndex()).findFirst().orElse(null);
            if (correspondingStep != null) {
                correspondingStep.setTimesExecuted(step.getTimesExecuted());
            }

        }
    }

    private String acceptRecoveryRequest(RecoveryRequest request) {

        String result;
        /* Exists some recovery */
        if (currentRequest != null) {

            /* Preemption */
            if (request.isWithInterrupt()) {
                logger.debug("Currently executed recovery will be interrupted");
                result = "acceptedWithPreemption";

            } else if (request.isSameProblem() && recoverySequenceController.getCurrentStatus() == ch.cern.cms.daq.expertcontroller.RecoveryStatus.Observe) {
                logger.debug("Currently recovery continues with next condition");
                result = "acceptedToContinue";
            } else if (request.isWithPostponement()) {
                logger.info("This request will be postponed");
                result = "acceptedToPostpone";

            }
            /* Rejection */
            else {
                result = "rejected";
            }

        }
        /* No recovery - accept */
        else {
            logger.debug("This is what is gonna happen: " + request);
            result = "accepted";
        }

        return result;

    }


    /**
     * Set up recovery request. This means that the recovery has not been filtered out and now the approval request to
     * operator will be issued
     */
    private void setupRecoveryRequest(RecoveryRequest request) {

        currentRequest = request;
        Long id = recoverySequenceController.getMainRecord().getId();

        executor.execute(() -> {
            dashboardController.notifyRecoveryStatus(getStatus());
            dashboardController.requestApprove(new ApprovalRequest(id));
        });

    }

    public ch.cern.cms.daq.expertcontroller.websocket.RecoveryStatus getStatus() {


        RecoveryRecord recoveryRecord = recoverySequenceController.getMainRecord();
        if (recoveryRecord != null) {

            ch.cern.cms.daq.expertcontroller.websocket.RecoveryStatus response = new ch.cern.cms.daq.expertcontroller.websocket.RecoveryStatus();

            response.setAutomatedSteps(new ArrayList<>());
            response.setId(recoveryRecord.getId());
            response.setStartDate(recoveryRecord.getStart());
            response.setEndDate(recoveryRecord.getEnd());

            String status;
            List<RecoveryRequestStep> recoverySteps;
            if (currentRequest != null) {
                status = currentRequest.getStatus();
                recoverySteps = currentRequest.getRecoverySteps();
            } else if (lastRequest != null) {
                status = "finished";
                recoverySteps = lastRequest.getRecoverySteps();
            } else {
                throw new RuntimeException("Cannot process status request for recovery with id " + recoveryRecord.getId());
            }

            response.setStatus(status);

            logger.info("Getting related conditions for record: " + recoveryRecord.getName());
            response.setConditionIds(recoveryRecord.getRelatedConditions().stream().collect(Collectors.toList()));

            for (RecoveryRequestStep recoveryRequestStep : recoverySteps) {
                RecoveryStepStatus stepStatus = new RecoveryStepStatus();
                stepStatus.setStarted(recoveryRequestStep.getStarted());
                stepStatus.setFinished(recoveryRequestStep.getFinished());
                stepStatus.setRcmsStatus(recoveryRequestStep.getStatus());
                stepStatus.setTimesExecuted(recoveryRequestStep.getTimesExecuted());
                if (recoveryRequestStep.getFinished() != null) {
                    stepStatus.setStatus("finished");
                } else {
                    if (recoveryRequestStep.getStarted() != null) {
                        stepStatus.setStatus("recovering");
                    } else {
                        stepStatus.setStatus("new");
                    }
                }
                stepStatus.setStepIndex(recoveryRequestStep.getStepIndex());
                response.getAutomatedSteps().add(stepStatus);
            }

            logger.info("The recovery request with id " + recoveryRecord.getId() + " has status: " + response);
            return response;

        } else {
            return null;
        }

    }

    @Deprecated
    public String getStatus(Long id, int stepIndex) {
        Optional<RecoveryRequest> recovery = recoveryJobRepository.findById(id);
        if (recovery.isPresent()) {
            RecoveryRequestStep step = recovery.get().getRecoverySteps().stream().filter(s -> s.getStepIndex() == stepIndex).findFirst().orElse(null);

            if (step == null) {
                return "step not found";
            }

            String status = step.getStatus();
            logger.info("The step " + stepIndex + " of recovery request with id " + id + " has status " + status);
            return status;

        } else {
            return "unknown";
        }

    }


    //TODO: process the whole procedure
    public void approveProcedure(long id) {
    }

    /**
     * Verify if the step identification numbers are correct. Find the corresponding step. Update current recovery
     * request fields. Execute step.
     *
     * @param id
     * @param step
     */
    public void approvedStep(long id, int step) {
        // ask shifter for confirmation - this can be timed out by expert

        logger.info("Available requests awaiting approval: " + currentRequest);

        if (currentRequest != null && recoverySequenceController.getMainRecord().getId() == id) {

            logger.info("Request " + id + " will be executed");
            RecoveryRequest request = currentRequest;
            RecoveryRequestStep recoveryRequestStep = request.getRecoverySteps().stream().filter(
                    s -> s.getStepIndex() == step
            ).findAny().orElseThrow(
                    () -> new RuntimeException("Incorrect step index")
            );

            recoveryRequestStep.setStatus("approved");
            recoveryRequestStep.setStarted(new Date());
            recoveryRequestStep.setFinished(null);
            if (recoveryRequestStep.getTimesExecuted() == null) {
                recoveryRequestStep.setTimesExecuted(0);
            }

            recoverySequenceController.accept(recoveryRequestStep.getStepIndex(), recoveryRequestStep.getHumanReadable());
            recoveryJobRepository.save(request);
            dashboardController.notifyRecoveryStatus(getStatus());

            try {

                rcmsController.recoverAndWait(request, recoveryRequestStep);
                recoveryRequestStep.setStatus("finished");
                recoveryRequestStep.setTimesExecuted(recoveryRequestStep.getTimesExecuted() + 1);
                recoveryRequestStep.setFinished(new Date());
                recoveryJobRepository.save(request);
                recoverySequenceController.stepCompleted();

                dashboardController.notifyRecoveryStatus(getStatus());

            } catch (LV0AutomatorControlException e) {

                recoveryRequestStep.setStarted(new Date());
                recoveryJobRepository.save(request);
                dashboardController.notifyRecoveryStatus(getStatus());
                e.printStackTrace();
            }

            // report to shifter what happened and say what to do next
            logger.info("This is what happened. ");
        } else {
            logger.warn("Could not found recovery to approve with given id " + id);
        }

    }

    public String handleDecision(ApprovalResponse approvalResponse) {
        Long recoveryId = approvalResponse.getRecoveryId();
        Integer step = approvalResponse.getStep();

        if (recoveryId == null) {
            throw new IllegalArgumentException("The 'recoveryId' parameter must not be null or empty");
        }
        if (approvalResponse.isApproved() == null) {
            throw new IllegalArgumentException("The 'approved' parameter must not be null or empty");
        }


        // decision regarding single step
        if (step != null) {

            if (approvalResponse.isApproved()) {
                logger.info("Operator approved recovery step " + step + " of recovery procedure " + recoveryId);
                approvedStep(recoveryId, step);
                return "Recovery step successfully approved";
            } else {

                logger.info("Operator rejected recovery step " + step + " of recovery procedure " + recoveryId);
                //TODO: handle
                return "Recovery step successfully rejected";
            }
        }

        // whole recovery procedure decision
        else {
            if (approvalResponse.isApproved()) {

                logger.info("Operator approved whole recovery procedure " + recoveryId);
                //TODO handle
                return "Recovery procedure successfully approved";
            } else {

                logger.info("Operator rejected whole recovery procedure " + recoveryId);
                // TODO handle
                return "Recovery procedure successfully rejected";
            }
        }

    }

    public void handleRecoveryStateUpdate(RecoveryRequest request) {
        recoveryJobRepository.save(request);
        dashboardController.notifyRecoveryStatus(getStatus());

    }

    public void endRecovery() {

        currentRequest.setStatus("finished");
        recoveryJobRepository.save(currentRequest);
        dashboardController.notifyRecoveryStatus(getStatus());
        lastRequest = currentRequest;

        if (waitingRequest == null) {
            currentRequest = null;
        } else {
            recoverySequenceController.start(waitingRequest);
            setupRecoveryRequest(waitingRequest);
            waitingRequest = null;
        }


    }

    public void finished(Long id) {

        if(recoverySequenceController.getMainRecord() != null
                && recoverySequenceController.getMainRecord().getRelatedConditions() != null
                && recoverySequenceController.getMainRecord().getRelatedConditions().contains(id)){

        //if (currentRequest != null && currentRequest.getProblemId() == id) {
            if (RecoveryStatus.AwaitingApproval == recoverySequenceController.getCurrentStatus()) {
                recoverySequenceController.end();
            } else {
                logger.info("Ignoring finished signal, not in awaiting approval state");
            }
        } else {
            logger.info("Ignoring finished signal, no condition with " + id);
        }

        if (waitingRequest != null && waitingRequest.getProblemId() == id) {
            logger.info("Removing waiting request, condition with id " + id + " has finished");
            waitingRequest = null;
        }
    }
}
