package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.api.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.api.RecoveryRequestStep;
import ch.cern.cms.daq.expertcontroller.api.RecoveryResponse;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryJobRepository;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecord;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecordRepository;
import ch.cern.cms.daq.expertcontroller.rcmsController.LV0AutomatorControlException;
import ch.cern.cms.daq.expertcontroller.rcmsController.RcmsController;
import ch.cern.cms.daq.expertcontroller.websocket.*;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;


/**
 * Responsible for managing the whole recovery process
 */
@Component("recoveryService")
public class RecoveryService {

    private final static Logger logger = Logger.getLogger(RecoveryService.class);
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

    private Set<Long> ongoingProblems = Collections.synchronizedSet(new HashSet<>());
    private Set<Long> preemptedProblems = Collections.synchronizedSet(new HashSet<>());

    @PreDestroy
    public void shutdown(){
        logger.info("Recovery service is going down. Ending the ongoing recovery if exists.");


        if(currentRequest != null) {
            RecoveryRecord mainRecord = recoverySequenceController.getMainRecord();
            if(mainRecord != null){
                String description = mainRecord.getDescription();
                description = description == null? "" : description + " ";
                mainRecord.setDescription(description + "Interrupted by service shutdown.");
            }
            recoverySequenceController.end();
        }
    }

    public Set<Long> getOngoingProblems() {
        return ongoingProblems;
    }

    /**
     * Submit new recovery request. This method will handle everything from filtering the request, requesting operator
     * approval to performing the recovery and persisting the status.
     *
     * Note that this method is asynchronous. It will return the
     *
     * @param request submitted request
     *
     * @return recovery response
     */
    public RecoveryResponse submitRecoveryRequest(RecoveryRequest request) {

        logger.info("New request has been submitted " + request.getIdentifyingString());

        RecoveryResponse response = new RecoveryResponse();
        String acceptanceDecision = acceptRecoveryRequestForExecution(request);

        response.setStatus(acceptanceDecision);
        request.setReceived(new Date());

        ongoingProblems.add(request.getProblemId());

        //TODO: we don't want strings here, replace it with enums
        switch (acceptanceDecision) {
            case "accepted":
                logger.info("Accepted recovery " + request.getIdentifyingString());
                request.setStatus("awaiting approval");
                recoveryJobRepository.save(request);
                recoverySequenceController.start(request);

                setupRecoveryRequest(request);
                break;
            case "acceptedWithPreemption":
                logger.info("Accepted with preemption recovery " + request.getIdentifyingString() + ". Previous recovery: " + currentRequest.getIdentifyingString() + " preempted.");
                preemptedProblems.add(currentRequest.getProblemId());
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

                logger.info("Accepted to continue recovery " + request.getIdentifyingString());
                request.setStatus("acceptedToContinue");
                response.setContinuesTheConditionId(currentRequest.getProblemId());

                getStepExecutionCount(currentRequest, request);
                recoveryJobRepository.save(request);
                recoverySequenceController.continueSame(request);


                setupRecoveryRequest(request); // pass current request
                break;
            case "acceptedToPostpone":

                logger.info("Accepted to postpone recovery " + request.getIdentifyingString());
                request.setStatus("acceptedToPostpone");
                recoveryJobRepository.save(request);
                waitingRequest = request;

                break;
            case "rejected":
                logger.info("Rejected recovery " + request.getIdentifyingString());
                request.setStatus("rejected");
                recoveryJobRepository.save(request);
                response.setRejectedDueToConditionId(currentRequest.getProblemId());
                break;
            default:
                break;
        }

        logger.info("Request " + request.getIdentifyingString() + " has been " + acceptanceDecision);
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

    /**
     * Decides whether recovery request will be accepted to execute.
     *
     * @param request submitted request
     *
     * @return decision, can be one of the following:
     * <ul>
     * <li>accepted - when no other recovery is currently being executed</li>
     * <li>acceptedWithPreemption - when submitted recovery is more severe than currently executed (DAQExpert decides
     * and sets up isWithInterrupt flag)</li>
     * <li>acceptedToContinue - when submitted recovery is the same as currently executed and current is in observe
     * period</li>
     * <li>acceptedToPostpone - when </li>
     * </ul>
     */
    private String acceptRecoveryRequestForExecution(RecoveryRequest request) {

        String result;
        /* Exists some recovery */
        if (currentRequest != null) {

            logger.info("There is currently request being handled: " + currentRequest.getIdentifyingString());

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

    public void continueSameProblem() {

        //getStepExecutionCount(currentRequest, request);
        //recoveryJobRepository.save(request);
        recoverySequenceController.continueSame(currentRequest);
        setupRecoveryRequest(currentRequest); // pass current request

    }

    /**
     * Set up recovery request. This means that the recovery has not been filtered out and now the approval request to
     * operator will be issued
     */
    private void setupRecoveryRequest(RecoveryRequest request) {

        logger.info("Recovery setup, current recovery is now " + request.getIdentifyingString());
        currentRequest = request;
        Long id = recoverySequenceController.getMainRecord().getId();

        executor.execute(() -> {
            dashboardController.notifyRecoveryStatus(getStatus());
            dashboardController.requestApprove(new ApprovalRequest(id));
        });

    }

    public RecoveryStatusDTO getStatus() {


        RecoveryRecord recoveryRecord = recoverySequenceController.getMainRecord();
        if (recoveryRecord != null) {

            RecoveryStatusDTO response = new RecoveryStatusDTO();

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

            logger.debug("Getting related conditions for record: " + recoveryRecord.getName());
            response.setConditionIds(recoveryRecord.getRelatedConditions().stream().collect(Collectors.toList()));

            for (RecoveryRequestStep recoveryRequestStep : recoverySteps) {
                RecoveryStepStatusDTO stepStatus = new RecoveryStepStatusDTO();
                stepStatus.setStarted(recoveryRequestStep.getStarted());
                stepStatus.setFinished(recoveryRequestStep.getFinished());

                // TODO: in case TTCHardReset was not accepted indicate this here
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

            logger.debug("The recovery request with id " + recoveryRecord.getId() + " has status: " + response);
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

        logger.info("Available request awaiting approval: " + currentRequest.getIdentifyingString());

        if (currentRequest != null && recoverySequenceController.getMainRecord().getId() == id) {

            logger.info("Request " + id + " will be executed");
            final RecoveryRequest request = currentRequest;
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

                rcmsController.execute(request, recoveryRequestStep);
                recoveryRequestStep.setStatus("finished");
                recoveryRequestStep.setTimesExecuted(recoveryRequestStep.getTimesExecuted() + 1);
                recoveryRequestStep.setFinished(new Date());
                recoveryJobRepository.save(request);
                recoverySequenceController.stepCompleted(request.getProblemId());

                dashboardController.notifyRecoveryStatus(getStatus());

            } catch (LV0AutomatorControlException e) {

                recoveryRequestStep.setStarted(new Date());
                recoveryJobRepository.save(request);
                dashboardController.notifyRecoveryStatus(getStatus());
                e.printStackTrace();
            }

            // TODO: report to shifter what happened and say what to do next
            logger.trace("This is what happened. ");
        } else {
            logger.warn("Could not found recovery to approve with given id " + id);
        }

    }

    /**
     * Handle decision.
     *
     * @param approvalResponse object representing decision of the operator.
     * @return
     */
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

        logger.info("Current recovery " + currentRequest.getProblemId() + " (" + currentRequest.getProblemTitle() + ") finishes");

        currentRequest.setStatus("finished");
        recoveryJobRepository.save(currentRequest);
        dashboardController.notifyRecoveryStatus(getStatus());
        lastRequest = currentRequest;
        currentRequest = null;

        if (waitingRequest == null) {
            logger.info("There is no more recovery now");
        } else {
            logger.info("There is queued waiting recovery " + waitingRequest.getIdentifyingString());

            if (ongoingProblems.contains(waitingRequest.getProblemId())) {
                logger.info("Waiting recovery is related to ongoing problem, will now become current");
                recoverySequenceController.start(waitingRequest);
                setupRecoveryRequest(waitingRequest);
            } else {
                logger.info("Waiting request is no longer within ongoing problems, will be ignored");
            }
            waitingRequest = null;
        }


    }

    /**
     * Called when conditions ends for any reason. E.g when condition was resolved by recovery action issued by this
     * application or by any external factors.
     *
     * @param id id of the
     */
    public void finished(Long id) {

        ongoingProblems.remove(id);

        if (recoverySequenceController.getMainRecord() != null
                && recoverySequenceController.getMainRecord().getRelatedConditions() != null
                && recoverySequenceController.getMainRecord().getRelatedConditions().contains(id)) {

            //if (currentRequest != null && currentRequest.getProblemId() == id) {
            if (RecoveryStatus.AwaitingApproval == recoverySequenceController.getCurrentStatus()) {
                recoverySequenceController.end();
            } else {
                logger.info("Ignoring finished signal of problem " + id + ", not in awaiting approval state");
            }
        } else {
            logger.info("Ignoring finished signal, no condition with " + id);
        }

        if (waitingRequest != null && waitingRequest.getProblemId() == id) {
            logger.info("Removing waiting request, condition with id " + id + " has finished");
            waitingRequest = null;
        }
    }

    @Override
    public String toString() {
        return "RecoveryService{" +
                "executor=" + executor +
                ", recoveryJobRepository=" + recoveryJobRepository +
                ", recoveryRecordRepository=" + recoveryRecordRepository +
                ", recoverySequenceController=" + recoverySequenceController +
                ", dashboardController=" + dashboardController +
                ", currentRequest=" + currentRequest +
                ", lastRequest=" + lastRequest +
                ", waitingRequest=" + waitingRequest +
                ", ongoingProblems=" + ongoingProblems +
                '}';
    }

    /**
     * Method to check weather given problem was preempted
     *
     * @param problemId problem id
     * @return true if problem was preempted, false otherwise
     */
    public boolean receivedPreemption(Long problemId) {

        logger.info("Checking whether post-observe actions should be applied for " + problemId + ", currently there are following preempted problems registered: " + preemptedProblems);

        if (preemptedProblems.contains(problemId)) {
            preemptedProblems.remove(problemId);
            return true;
        } else {
            return false;
        }
    }

    public Long getCurrentRequestId() {
        if(currentRequest != null){
            return currentRequest.getProblemId();
        }
        return null;
    }

}
