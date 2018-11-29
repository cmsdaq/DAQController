package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalRequest;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryEvent;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.repository.RecoveryEventRepository;
import ch.cern.cms.daq.expertcontroller.repository.RecoveryJobRepository;
import ch.cern.cms.daq.expertcontroller.repository.RecoveryProcedureRepository;
import ch.cern.cms.daq.expertcontroller.service.IRecoveryService;
import ch.cern.cms.daq.expertcontroller.service.LoopBreaker;
import ch.cern.cms.daq.expertcontroller.service.rcms.LV0AutomatorControlException;
import ch.cern.cms.daq.expertcontroller.service.rcms.RcmsController;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSM;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMEvent;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMListener;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.IFSMListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import rcms.fm.fw.service.command.CommandServiceException;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class ExecutorFactory {

    @Autowired
    RcmsController rcmsController;

    @Autowired
    RecoveryProcedureRepository recoveryProcedureRepository;

    @Autowired
    RecoveryJobRepository recoveryJobRepository;

    @Autowired
    RecoveryEventRepository recoveryEventRepository;

    @Autowired
    IRecoveryService recoveryService;

    @Autowired
    IExecutor executor;

    @Autowired
    LoopBreaker loopBreaker;

    @Value("${observe.period}")
    private Integer observePeriod;

    protected static RcmsController srcmsController;
    protected static RecoveryProcedureRepository srecoveryProcedureRepository;
    protected static RecoveryJobRepository srecoveryJobRepository;
    protected static RecoveryEventRepository srecoveryEventRepository;
    protected static IRecoveryService srecoveryService;
    protected static IExecutor sexecutor;
    static private Integer sobservePeriod;
    protected static LoopBreaker sloopBreaker;


    @PostConstruct
    public void init() {
        ExecutorFactory.srcmsController = rcmsController;
        ExecutorFactory.srecoveryProcedureRepository = recoveryProcedureRepository;
        ExecutorFactory.srecoveryJobRepository = recoveryJobRepository;
        ExecutorFactory.srecoveryEventRepository = recoveryEventRepository;
        ExecutorFactory.srecoveryService = recoveryService;
        ExecutorFactory.sexecutor = executor;
        ExecutorFactory.sobservePeriod = observePeriod;
        ExecutorFactory.sloopBreaker = loopBreaker;

        logger.info(String.format("Observe period is %s ms", sobservePeriod));

        rcmsController.setRcmsStatusConsumer(rcmsStatusChangeConsumer);
    }

    private static Logger logger = LoggerFactory.getLogger(ExecutorFactory.class);

    public static IExecutor build(
            Function<RecoveryJob, FSMEvent> manualJobApprovalConsumer,
            Function<RecoveryJob, FSMEvent> automaticJobApprovalConsumer,
            ExecutionMode mode,
            Function<RecoveryJob, FSMEvent> recoveryJobConsumer,
            BiConsumer<RecoveryProcedure, List<RecoveryEvent>> report,
            Supplier<FSMEvent> observer,
            Integer approvalTimeout,
            Integer executionTimeout,
            Consumer<RecoveryProcedure> persistConsumer,
            Consumer<RecoveryProcedure> onUpdateConsumer,
            Supplier<Boolean> availabilityConsumer,
            Runnable interruptConsumer,
            Function<RecoveryProcedure, Boolean> loopBreakerConsumer
    ) {

        IFSMListener listener = FSMListener.builder()
                .persistResultsConsumer(persistConsumer)
                .onUpdateConsumer(onUpdateConsumer)
                .build();


        FSM fsm = FSM.builder().listener(listener).build().initialize();
        IExecutor executor = Executor.builder()
                .fsm(fsm)
                .listener(listener)
                .executorService(Executors.newFixedThreadPool(1))
                .manualJobApprovalConsumer(manualJobApprovalConsumer)
                .automatedApprovalConsumer(automaticJobApprovalConsumer)
                .executionMode(mode)
                .jobConsumer(recoveryJobConsumer)
                .statusReportConsumer(report)
                .observationConsumer(observer)
                .approvalTimeout(approvalTimeout)
                .executionTimeout(executionTimeout)
                .interruptConsumer(interruptConsumer)
                .isBusyConsumer(availabilityConsumer)
                .loopBreakerConumer(loopBreakerConsumer)
                .build();
        ((FSMListener) listener).setExecutor(executor);


        return executor;
    }

    public static Function<RecoveryJob, FSMEvent> automaticApprovalConsumer = recoveryJob -> {

        logger.info("Automatic approval consumer will approve after 1s");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.info("Approval required, accepting");
        return FSMEvent.JobAccepted;

    };

    public static Function<RecoveryJob, FSMEvent> manualApprovalConsumer = recoveryJob -> {

        ApprovalRequest approvalRequest = ApprovalRequest.builder()
                .recoveryProcedureId(recoveryJob.getProcedureId())
                .defaultStepIndex(recoveryJob.getStepIndex())
                .build();

        srecoveryService.onApprovalRequest(approvalRequest);

        //TODO: change it to using automatic approval consumer
        if (sexecutor.isForceAccept()) {
            return FSMEvent.JobAccepted;
        }

        return null;

    };

    public static Function<RecoveryJob, FSMEvent> recoveryJobConsumer = recoveryJob -> {
        try {
            logger.debug("Passing the recovery job: " + recoveryJob.toCompactString() + " to RCMS controller");
            srcmsController.execute(recoveryJob);
            return FSMEvent.JobCompleted;
        } catch (CommandServiceException | LV0AutomatorControlException e) {
            logger.warn("Job failed due to RCMS exception: " + e.getMessage());
            return FSMEvent.JobException;
        }
    };


    public static Supplier<FSMEvent> fixedDelayObserver = () -> {

        logger.info("Observing the system for " + sobservePeriod + " ms");

        try {
            Thread.sleep(sobservePeriod);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(sexecutor.isReceivedFinished()){

            logger.info("Observation finished and received finished signal");
            return FSMEvent.Finished;

        } else{
            logger.info("Timeout of observation");
            return FSMEvent.NoEffect;
        }


    };

    public static Consumer<String> rcmsStatusChangeConsumer = status -> {

        logger.info("Consuming new RCMS status: " + status);
        sexecutor.rcmsStatusUpdate(status);
    };

    public static Consumer<RecoveryProcedure> persistResultsConsumer = recoveryProcedure -> {

        logger.info("Updating recovery procedure " + recoveryProcedure.getId());
        recoveryProcedure.getExecutedJobs().stream().forEach(job -> {

            srecoveryJobRepository.save(job);
        });

        recoveryProcedure.getEventSummary().stream().forEach(event->{
            srecoveryEventRepository.save(event);
        });
        srecoveryProcedureRepository.save(recoveryProcedure);


    };

    public static Consumer<RecoveryProcedure> onProcedureUpdateConsumer = recoveryProcedure -> {
        logger.info("On recovery procedure update " + recoveryProcedure.getId());
        srecoveryService.onRecoveryProcedureStateUpdate();
    };

    public static Runnable interruptConsumer = () -> {
        logger.info("Interrupting rcms job");
        srcmsController.interrupt();

    };

    public static Runnable interruptFakeConsumer = () -> {
        logger.info("Fake interrupting rcms job");

    };

    public static Supplier<Boolean> fakeIsAvailableSupplier = () -> true;

    public static Function<RecoveryJob, FSMEvent> recoveryJobFakeConsumer = recoveryJob -> {

        logger.info("Passing the recovery job: " + recoveryJob.toCompactString() + " to fake RCMS controller");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return FSMEvent.JobCompleted;

    };

    public static Supplier<Boolean> isAvailableSupplier = () -> {
        Boolean recoveryOngoing = srcmsController.isRecoveryOngoing();
        boolean isAvailable = false;
        logger.info(String.format("Checking availability of RCMS, is recovery ongoing: %s ", recoveryOngoing));
        if (recoveryOngoing != null && !recoveryOngoing) {
            isAvailable = true;
        }
        return isAvailable;
    };

    public static Function<RecoveryProcedure, Boolean> loopBreakerConsumer = recoveryProcedure -> {

        boolean preventDueToLoopDetected = false;

        if (recoveryProcedure.getExecutionMode() == ExecutionMode.Automated &&
                sloopBreaker.exceedsTheLimit()) {
            logger.info(String.format(
                    "Loop detected," +
                            " automated execution limit reached. " +
                            "Limit is maximum %s automatic recoveries oover time period of %s seconds",
                    sloopBreaker.getNumberOfExecutions(), sloopBreaker.getTimeWindow()));
            preventDueToLoopDetected = true;
        }

        if (recoveryProcedure.getExecutionMode() == ExecutionMode.Automated &&
                !preventDueToLoopDetected) {
            sloopBreaker.registerAccepted();
        }
        return preventDueToLoopDetected;

    };

    public static BiConsumer<RecoveryProcedure, List<RecoveryEvent>> report = (rp, s) -> {
        logger.info("Reporting: " + s);
        // steps are preserved anyway
    };

    public static IExecutor DEFAULT_EXECUTOR = build(
            manualApprovalConsumer,
            automaticApprovalConsumer,
            ExecutionMode.ApprovalDriven,
            recoveryJobConsumer,
            report,
            fixedDelayObserver,
            3600, //TODO: 1h of timeout
            600,
            persistResultsConsumer,
            onProcedureUpdateConsumer,
            isAvailableSupplier,
            interruptConsumer,
            loopBreakerConsumer

    );

    public static IExecutor FAKE_EXECUTOR = build(
            manualApprovalConsumer,
            automaticApprovalConsumer,
            ExecutionMode.ApprovalDriven,
            recoveryJobFakeConsumer,
            report,
            fixedDelayObserver,
            60,
            600,
            persistResultsConsumer,
            onProcedureUpdateConsumer,
            fakeIsAvailableSupplier,
            interruptFakeConsumer,
            loopBreakerConsumer

    );


}
