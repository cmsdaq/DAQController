package ch.cern.cms.daq.expertcontroller.service;

import ch.cern.cms.daq.expertcontroller.controller.DashboardController;
import ch.cern.cms.daq.expertcontroller.datatransfer.*;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryEvent;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.repository.RecoveryProcedureRepository;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.ExecutionMode;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.ExecutorStatus;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.IExecutor;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.State;
import org.apache.log4j.Logger;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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


    protected ScheduledThreadPoolExecutor delayedFinishedSignals;

    private final static Logger logger = Logger.getLogger(DefaultRecoveryService.class);

    @PostConstruct
    private void init() {
        delayedFinishedSignals = new ScheduledThreadPoolExecutor(1);
    }

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

        if (request == null) {
            throw new IllegalArgumentException("Recovery request cannot be empty");
        }
        if (request.getRecoveryRequestSteps() == null || request.getRecoveryRequestSteps().size() == 0) {
            throw new IllegalArgumentException("Recovery request should provide steps");
        }
        if (request.getRecoveryRequestSteps().stream().map(s -> s.getStepIndex()).filter(s -> s == null).count() > 0) {
            throw new IllegalArgumentException("Every recovery step in procedure should have 'stepIndex' assigned");
        }

        RecoveryResponse response = new RecoveryResponse();

        logger.debug("New request has been submitted " + request);

        String acceptanceDecision = acceptRecoveryRequestForExecution(request);
        response.setAcceptanceDecision(acceptanceDecision);


        switch (acceptanceDecision) {
            case "accepted":
                logger.info("Accepted recovery request: " + request);
                RecoveryProcedure recoveryProcedure = createRecoveryProcedure(request);

                if (request.getIsProbe() != null) {
                    recoveryProcedure.setIsProbe(request.getIsProbe());
                } else {
                    recoveryProcedure.setIsProbe(false);
                }

                recoveryProcedureRepository.save(recoveryProcedure);
                logger.debug("New procedure has been persisted with id " + recoveryProcedure.getId());
                recoveryProcedureExecutor.start(recoveryProcedure);
                response.setRecoveryProcedureId(recoveryProcedure.getId());


                break;
            case "acceptedWithPreemption":
                logger.info("Accepted with preemption recovery " + request);
                recoveryProcedureExecutor.interrupt();

                RecoveryProcedure preemptedProcedure = recoveryProcedureExecutor.getExecutedProcedure();
                recoveryProcedureRepository.save(preemptedProcedure);
                logger.info("Preempted procedure has been updated in db");

                RecoveryProcedure preemptingProcedure = createRecoveryProcedure(request);
                recoveryProcedureRepository.save(preemptingProcedure);
                logger.info("Preempting procedure has been persisted with id " + preemptedProcedure.getId());
                recoveryProcedureExecutor.start(preemptingProcedure);
                response.setRecoveryProcedureId(preemptingProcedure.getId());
                break;

            case "acceptedToContinue":

                logger.info("Accepted to continue recovery " + request);
                response.setRecoveryProcedureId(recoveryProcedureExecutor.getExecutedProcedure().getId());

                RecoveryProcedure procedureToContinue = recoveryProcedureExecutor.getExecutedProcedure();
                logger.info("Continue request brings new condition id: " + request.getProblemId());
                procedureToContinue.getProblemIds().add(request.getProblemId());
                recoveryProcedureExecutor.continueCurrentProcedure();
                //TODO: set which problem id is continued (Expert must match?)

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
            case "rejectedDueToManualRecovery":
                logger.info("Rejected due to manual recovery " + request);
                break;
            default:
                break;
        }

        logger.info("Request " + request + " has been handled with " + acceptanceDecision);
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


            ModelMapper modelMapper = new ModelMapper();
            modelMapper.getConfiguration()
                    .setMatchingStrategy(MatchingStrategies.STRICT);
            List<Event> executorStatus = modelMapper.map(status.getActionSummary(), List.class);
            builder.actionSummary(executorStatus);

        }
        return builder.build();
    }


    /**
     * This service status describes current executor status and last procedure. Last procedure can be finished or
     * ongoing. It's based on current state, database is not accessed.
     *
     * @return
     */
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
        RecoveryProcedureStatus.RecoveryProcedureStatusBuilder procedureStatusBuilder
                = RecoveryProcedureStatus.builder();
        if (lastExecutedProcedure != null) {
            if (lastExecutedProcedure.getStart() != null)
                procedureStatusBuilder.startDate(Date.from(lastExecutedProcedure.getStart().toInstant()));
            if (lastExecutedProcedure.getEnd() != null)
                procedureStatusBuilder.endDate(Date.from(lastExecutedProcedure.getEnd().toInstant()));
            procedureStatusBuilder.jobStatuses(null);

        }
        RecoveryProcedureStatus recoveryProcedureStatus = procedureStatusBuilder.build();

        // TODO: include how many times the steps were executed etc.
        // TODO: set up procedure id
        // TODO: set up related conditions
        // TODO: set up recovery requests


        // Following parameters will be handled differently depending on status of the executor
        List<RecoveryEvent> actionSummary = null;
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
            actionSummary = lastExecutedProcedure.getEventSummary();
            finalStatus = lastExecutedProcedure.getState();
            existLastProcedure = true;
        }


        if (existLastProcedure) {
            ModelMapper modelMapper = new ModelMapper();
            modelMapper.getConfiguration()
                    .setMatchingStrategy(MatchingStrategies.STRICT);

            if (actionSummary != null) {
                List<Event> actionSummaryDTO =
                        actionSummary.stream()
                                .map(e -> modelMapper.map(e, ch.cern.cms.daq.expertcontroller.datatransfer.Event.class))
                                .collect(Collectors.toList());

                recoveryProcedureStatus.setActionSummary(actionSummaryDTO);
            }
            recoveryProcedureStatus.setFinalStatus(finalStatus);
            recoveryProcedureStatus.setId(lastExecutedProcedure.getId());
            recoveryProcedureStatus.setConditionIds(lastExecutedProcedure.getProblemIds());

            ArrayList<RecoveryJobStatus> jobStatuses = new ArrayList<>();
            lastExecutedProcedure.getProcedure().stream().forEach(j -> {
                Date started = null, finished = null;
                if (j.getStart() != null)
                    started = Date.from(j.getStart().toInstant());

                if (j.getEnd() != null)
                    finished = Date.from(j.getEnd().toInstant());

                // count jobs with given step index

                logger.trace("Counting executed times for step " + j.getStepIndex());
                logger.trace("... from executed jobs: " + lastExecutedProcedure.getExecutedJobs());
                Long executed = 0L;
                if (lastExecutedProcedure.getExecutedJobs() != null)
                    executed = lastExecutedProcedure.getExecutedJobs().stream()
                            .filter(c -> c.getStepIndex() == j.getStepIndex())
                            .filter(c -> c.getEnd() != null).count();

                RecoveryJobStatus recoveryJobStatus = RecoveryJobStatus.builder()
                        .started(started)
                        .finished(finished)
                        .stepIndex(j.getStepIndex())
                        .status(j.getStatus())
                        .rcmsStatus(j.getRcmsStatus())
                        .timesExecuted(executed.intValue())
                        .build();

                jobStatuses.add(recoveryJobStatus);
            });

            recoveryProcedureStatus.setJobStatuses(jobStatuses);
            recoveryProcedureStatus.setIsProbe(lastExecutedProcedure.getIsProbe());

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


        Long procedureId = approvalResponse.getRecoveryProcedureId();
        Integer step = approvalResponse.getStep();
        boolean isProcedureContext = false;

        if (approvalResponse.getProcedureContext() != null && approvalResponse.getProcedureContext() == true) {
            isProcedureContext = true;
        }

        if (procedureId == null) {
            throw new IllegalArgumentException("The 'recoveryProcedureId' parameter must not be null or empty");
        }
        if (step == null) {
            if (!isProcedureContext)
                throw new IllegalArgumentException("The 'step' parameter must not be null or empty");
        }
        if (approvalResponse.getApproved() == null) {
            throw new IllegalArgumentException("The 'approved' parameter must not be null or empty");
        }

        if (isProcedureContext) {
            logger.info("Received approval response for whole procedure");
        } else {
            logger.info("Received approval response for procedure: " + procedureId);
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

        RecoveryServiceStatus recoveryServiceStatus = getRecoveryServiceStatus();

        dashboardController.notifyRecoveryStatus(recoveryServiceStatus);
    }


    public void onApprovalRequest(ApprovalRequest approvalRequest) {
        logger.info("Asking clients for appproval " + approvalRequest);
        dashboardController.requestApprove(approvalRequest);
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

    /**
     * Generate Recovery Procedure from given Recovery Request
     *
     * @param recoveryRequest recovery request that will bee a base for cration of Recovery procedure
     */
    private RecoveryProcedure createRecoveryProcedure(RecoveryRequest recoveryRequest) {

        ExecutionMode procedureExecutionMode = ExecutionMode.ApprovalDriven;
        if(recoveryRequest.isAutomatedRecoveryEnabled())
            procedureExecutionMode = ExecutionMode.Automated;


        RecoveryProcedure recoveryProcedure =
                RecoveryProcedure.builder().
                        procedure(recoveryRequest.getRecoveryRequestSteps().stream()
                                          .map(c -> RecoveryJob.builder()
                                                  .job(c.getHumanReadable())
                                                  .stepIndex(c.getStepIndex())
                                                  .greenRecycle(c.getGreenRecycle())
                                                  .redRecycle(c.getRedRecycle())
                                                  .reset(c.getReset())
                                                  .fault(c.getFault())
                                                  .issueTTCHardReset(c.getIssueTTCHardReset())
                                                  .build())
                                          .collect(Collectors.toList()))
                        .problemTitle(recoveryRequest.getProblemTitle())
                        .executedJobs(new ArrayList<>())
                        .executionMode(procedureExecutionMode)
                        .problemIds(new ArrayList<>(Arrays.asList(recoveryRequest.getProblemId())))
                        .build();

        if (recoveryProcedure.getProcedure() == null || recoveryProcedure.getProcedure().size() == 0) {
            throw new IllegalArgumentException("Recovery procedure has no jobs");
        }

        logger.info("Procedure built: " + recoveryProcedure);

        return recoveryProcedure;
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
     * <li>rejected</li>
     * <li>rejectedDueToManualRecovery</li>
     * </ul>
     */
    protected String acceptRecoveryRequestForExecution(RecoveryRequest request) {

        String result;
        ExecutorStatus executorStatus = recoveryProcedureExecutor.getStatus();
        RecoveryProcedure recoveryProcedure = recoveryProcedureExecutor.getExecutedProcedure();

        if (executorStatus.getState() == State.Idle) {
            if (recoveryProcedureExecutor.isAvailable()) {
                logger.debug("Following request has been accepted: " + request);
                result = "accepted";
            } else {
                result = "rejectedDueToManualRecovery";
            }
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


    /**
     * Handle reception of finished signal
     *
     * @param id id of problem that finished
     */
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

                    boolean accepted = recoveryProcedureExecutor.finished();

                    if (!accepted) {
                        delayedFinishedSignals.schedule(() -> this.finished(id), 1, TimeUnit.SECONDS);
                    }
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
    public InterruptResponse interrupt() {
        logger.info("Handling interrupting signal");
        if (recoveryProcedureExecutor.getStatus().getState() != State.Idle) {
            recoveryProcedureExecutor.interrupt();
            return InterruptResponse.builder().status("accepted").message("").build();
        } else {
            logger.info("Executor is in idle state, ignoring interrupt signal");
            return InterruptResponse.builder()
                    .status("ignored")
                    .message("Executor is in idle state, ignoring interrupt signal").build();
        }
    }

    @Override
    public String enableAutomation(boolean enabled) {
        if (enabled) {
            if (recoveryProcedureExecutor.getExecutionMode() == ExecutionMode.Automated) {
                return "Automation is already enabled";
            }
            recoveryProcedureExecutor.setExecutionMode(ExecutionMode.Automated);
            return "Automation has been enabled";
        } else {

            if (recoveryProcedureExecutor.getExecutionMode() == ExecutionMode.ApprovalDriven) {
                return "Automation is already disabled";
            }
            recoveryProcedureExecutor.setExecutionMode(ExecutionMode.ApprovalDriven);
            return "Automation has been disabled";
        }
    }

    @Override
    public String automationStatus(){
        return recoveryProcedureExecutor.getExecutionMode().toString();
    }
}
