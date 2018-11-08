package ch.cern.cms.daq.expertcontroller.service;

import ch.cern.cms.daq.expertcontroller.controller.DashboardController;
import ch.cern.cms.daq.expertcontroller.datatransfer.*;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.repository.RecoveryProcedureRepository;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.ExecutorStatus;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.IExecutor;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.State;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public abstract class DefaultRecoveryService implements IRecoveryService {


    @Autowired
    private DashboardController dashboardController;

    @Autowired
    private RecoveryProcedureRepository recoveryProcedureRepository;

    @Autowired
    protected IExecutor recoveryProcedureExecutor;


    /**
     * Less important request in comparison to currentRequest. It will be executed if and only if currentRequest was
     * fully processed and condition that generated waitingRequest has not been finished TODO: is it neccessary
     */
    private RecoveryRequest waitingRequest;

    private final static Logger logger = Logger.getLogger(DefaultRecoveryService.class);


    /**
     * Submit new recovery request. This method will handle everything from filtering the request, requesting operator
     * approval to performing the recovery and persisting the acceptanceDecision.
     * <p>
     * Note that this method is asynchronous. It will return the
     *
     * @param request submitted request
     * @return recovery response
     */
    public RecoveryResponse submitRecoveryRequest(RecoveryRequest request) {

        logger.info("New request has been submitted " + request);

        RecoveryResponse response = new RecoveryResponse();
        String acceptanceDecision = acceptRecoveryRequestForExecution(request);

        response.setAcceptanceDecision(acceptanceDecision);


        switch (acceptanceDecision) {
            case "accepted":
                logger.info("Accepted recovery request: " + request);
                RecoveryProcedure recoveryProcedure = createRecoveryProcedure(request);

                //TODO: thread should be spanned here - isn't it now?

                recoveryProcedure.setStart(OffsetDateTime.now());
                recoveryProcedureRepository.save(recoveryProcedure);
                logger.info("New procedure has been persisted with id " + recoveryProcedure.getId());
                recoveryProcedureExecutor.start(recoveryProcedure);
                response.setRecoveryProcedureId(recoveryProcedure.getId());

                break;
            case "acceptedWithPreemption":
                logger.info("Accepted with preemption recovery " + request);

                //TODO: save preempted? preemptedProblems.add(currentRequest.getProblemId());
                recoveryProcedureExecutor.interrupt();

                RecoveryProcedure preemptedProcedure = recoveryProcedureExecutor.getExecutedProcedure();
                recoveryProcedureRepository.save(preemptedProcedure);
                logger.info("Preempted procedure has been updated in db");

                RecoveryProcedure preemptingProcedure = createRecoveryProcedure(request);
                recoveryProcedureExecutor.start(preemptingProcedure);
                recoveryProcedureRepository.save(preemptingProcedure);
                logger.info("Preempting procedure has been persisted with id " + preemptedProcedure.getId());

                response.setRecoveryProcedureId(preemptingProcedure.getId());
                break;

            case "acceptedToContinue":

                logger.info("Accepted to continue recovery " + request);
                response.setRecoveryProcedureId(recoveryProcedureExecutor.getExecutedProcedure().getId());

                //TODO: set which problem id is continued (Expert must match?)
                //TODO: set executor to continue
                //TODO: add problem id to recovery procedure


                break;
            case "acceptedToPostpone":

                logger.info("Accepted to postpone recovery " + request);

                //TODO: is it the correct way?
                //TODO: check if there is no other waiting request
                //TODO: perhaps comparing to waitingRequest will be enought (no need for queue of waiting request, only
                // 1 is enought)
                waitingRequest = request;

                break;
            case "rejected":
                logger.info("Rejected recovery " + request);
                Long conditionId = recoveryProcedureExecutor.getExecutedProcedure().getProblemIds().iterator().next();
                response.setRejectedDueToConditionId(conditionId);
                break;
            default:
                break;
        }

        logger.info("Request " + request + " has been " + acceptanceDecision);
        return response;

    }


    @Override
    public RecoveryProcedureStatus getRecoveryProcedureStatus(Long id) {


        ExecutorStatus status = recoveryProcedureExecutor.getStatus();

        RecoveryProcedureStatus.RecoveryProcedureStatusBuilder builder = RecoveryProcedureStatus.builder();
        builder.finalStatus(status.getState().toString());

        if (State.Idle != status.getState()) {

            // TODO: include how many times the steps were executed etc.
            // TODO: set up procedure id
            // TODO: set up related conditions
            // TODO: set up recovery requests
            builder.actionSummary(status.getActionSummary());

        }
        return builder.build();
    }

    private RecoveryProcedureStatus buildProcedureStatus(RecoveryProcedure recoveryProcedure) {

        RecoveryProcedureStatus.RecoveryProcedureStatusBuilder builder = RecoveryProcedureStatus.builder();
        if (recoveryProcedure != null) {
            if (recoveryProcedure.getStart() != null)
                builder.startDate(Date.from(recoveryProcedure.getStart().toInstant()));
            if (recoveryProcedure.getEnd() != null)
                builder.endDate(Date.from(recoveryProcedure.getEnd().toInstant()));
            builder.jobStatuses(null);

        }
        return builder.build();
    }

    @Override
    public RecoveryServiceStatus getRecoveryServiceStatus() {

        // 1. Get Executor status
        ExecutorStatus status = recoveryProcedureExecutor.getStatus();
        logger.info("State of executor is " + status.getState());

        // 2. Get Last Procedure
        RecoveryProcedure lastExecutedProcedure = recoveryProcedureExecutor.getExecutedProcedure();
        if (lastExecutedProcedure == null) {
            logger.info("No previous procedures");
        } else {
            logger.info("Last procedure: " + lastExecutedProcedure.getProblemTitle());
        }
        // 3. Prepare Recovery Service Status DTO
        RecoveryServiceStatus.RecoveryServiceStatusBuilder builder = RecoveryServiceStatus.builder();
        builder.executorState(status.getState().toString());

        // 4. Prepare last Procedure Status DTO
        RecoveryProcedureStatus recoveryProcedureStatus = buildProcedureStatus(lastExecutedProcedure);

        // TODO: include how many times the steps were executed etc.
        // TODO: set up procedure id
        // TODO: set up related conditions
        // TODO: set up recovery requests


        // Following parameters will be handled differently depending on status of the executor
        List<String> actionSummary = null;
        String finalStatus = null;
        boolean existLastProcedure = false;

        /* If currently executed - take procedure details from executor */
        if (State.Idle != status.getState()) {
            actionSummary = status.getActionSummary();
            finalStatus = status.getState().toString();
            existLastProcedure = true;
        }

        /* If finished - take last procedure (if exists) details from procedure object */
        else if (State.Idle == status.getState() && lastExecutedProcedure != null) {
            actionSummary = lastExecutedProcedure.getEventSummary().stream().map(c -> c.getContent()).collect(Collectors.toList());
            finalStatus = lastExecutedProcedure.getState();
            existLastProcedure = true;
        }


        if (existLastProcedure) {
            recoveryProcedureStatus.setActionSummary(actionSummary);
            recoveryProcedureStatus.setFinalStatus(finalStatus);
            recoveryProcedureStatus.setId(lastExecutedProcedure.getId());
            builder.lastProcedureStatus(recoveryProcedureStatus);
        }
        return builder.build();
    }

    /**
     * Handle decision.
     *
     * @param approvalResponse object representing decision of the operator.
     * @return
     */
    @Override
    public String submitApprovalDecision(ApprovalResponse approvalResponse) {


        Long recoveryId = approvalResponse.getRecoveryProcedureId();
        Integer step = approvalResponse.getStep();

        if (recoveryId == null) {
            throw new IllegalArgumentException("The 'recoveryProcedureId' parameter must not be null or empty");
        }
        if (approvalResponse.getApproved() == null) {
            throw new IllegalArgumentException("The 'approved' parameter must not be null or empty");
        }

        recoveryProcedureExecutor.approveRecovery(approvalResponse);

        //TODO: give some more detailed feedback (scheduled to execute/not found etc)
        return "processed";
    }


    /**
     * Whenever underlying elements update this should be passed to Dashboard
     * <p>
     * TODO: call this method whenever there is an update
     */
    public void onRecoveryProcedureStateUpdate() {

        // TODO: fill with recovery procedure finalStatus
        dashboardController.notifyRecoveryStatus(null);
    }


    /**
     * Called when Recovery procedure finishes
     */
    public void onRecoveryProcedureCompletion() {

        //TODO: decide if this is right
        if (waitingRequest != null) {
            logger.info("Recovery procedure completed, waiting request will now be picked up");
            waitingRequest = null;
        }
    }

    //TODO: convert Recovery Request to Recovery Procedure
    private RecoveryProcedure createRecoveryProcedure(RecoveryRequest recoveryRequest) {
        RecoveryProcedure recoveryJob =
                RecoveryProcedure.builder().
                        procedure(recoveryRequest.getRecoverySteps().stream()
                                          .map(c -> RecoveryJob.builder()
                                                  .job(c.getHumanReadable())
                                                  .stepIndex(c.getStepIndex())
                                                  .build())
                                          .collect(Collectors.toList()))
                        .problemTitle(recoveryRequest.getProblemTitle())
                        .problemIds(Arrays.asList(recoveryRequest.getProblemId()))
                        .build();

        return recoveryJob;
    }

    /**
     * Decides whether recovery request will be accepted to execute.
     *
     * @param request submitted request
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
    protected String acceptRecoveryRequestForExecution(RecoveryRequest request) {

        String result;
        ExecutorStatus executorStatus = recoveryProcedureExecutor.getStatus();
        RecoveryProcedure recoveryProcedure = recoveryProcedureExecutor.getExecutedProcedure();

        if (executorStatus.getState() == State.Idle) {
            logger.debug("Following request has been accepted: " + request);
            result = "accepted";
        } else {
            logger.info("There is procedure currently being executed: " + recoveryProcedure);

            /* Request with interruption */
            if (request.isWithInterrupt()) {
                logger.debug("Currently executed recovery will be interrupted");
                result = "acceptedWithPreemption";
            }

            /* Request to continue, TODO: confirm it's still the same problem (prevent from accepting very delayed answers) */
            else if (request.isSameProblem() && executorStatus.getState() == State.Observe) {
                logger.debug("Currently recovery continues with next condition");
                result = "acceptedToContinue";
            }

            /* Request with postponement */
            else if (request.isWithPostponement()) {
                logger.info("This request will be postponed");
                result = "acceptedToPostpone";
            }

            /* Reject */
            else {
                logger.info("This request will be rejected");
                result = "rejected";
            }

        }
        return result;
    }


    @Override
    public void finished(Long id) {

        logger.info("Handling finish signal for problem " + id);
        State executorState = recoveryProcedureExecutor.getStatus().getState();

        if (executorState != State.Idle) {

            boolean corresponds;
            try {
                corresponds = recoveryProcedureExecutor.getExecutedProcedure().getProblemIds().contains(id);
            } catch (NullPointerException e) {
                corresponds = false;
            }


            if (corresponds) {

                if (Arrays.asList(State.Observe, State.SelectingJob, State.AwaitingApproval, State.Recovering).contains(executorState)) {

                    logger.info("Executor in " + executorState + " state. Handling finish signal.");
                    recoveryProcedureExecutor.finished();
                } else {
                    logger.info("Finish signal will be ignored in current executor state: " + executorState);
                }

            } else {
                logger.info("Finish signal for problem " + id + " unrelated to currently executed recovery procedure: "
                                    + recoveryProcedureExecutor.getExecutedProcedure().getProblemIds());
            }

        } else {
            logger.info("Executor is in idle state, ignoring finished signal");
        }

    }

    @Override
    public void shutdown() {
        logger.info("Recovery service is going down");
        if (recoveryProcedureExecutor.getStatus().getState() != State.Idle) {
            logger.info("Ending the ongoing recovery procedure");
            recoveryProcedureExecutor.interrupt();
        }
    }

    @Override
    public void interrupt() {
        logger.info("Handling interrupting signal");
        if(recoveryProcedureExecutor.getStatus().getState() != State.Idle) {
            recoveryProcedureExecutor.interrupt();
        } else {
            logger.info("Executor is in idle state, ignoring interrupt signal");
        }
    }
}
