package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalRequest;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryEvent;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.repository.RecoveryJobRepository;
import ch.cern.cms.daq.expertcontroller.repository.RecoveryProcedureRepository;
import ch.cern.cms.daq.expertcontroller.service.IRecoveryService;
import ch.cern.cms.daq.expertcontroller.service.rcms.LV0AutomatorControlException;
import ch.cern.cms.daq.expertcontroller.service.rcms.RcmsController;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSM;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMEvent;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMListener;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.IFSMListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    IRecoveryService recoveryService;

    @Autowired
    IExecutor executor;

    protected static RcmsController srcmsController;
    protected static RecoveryProcedureRepository srecoveryProcedureRepository;
    protected static RecoveryJobRepository srecoveryJobRepository;
    protected static IRecoveryService srecoveryService;
    protected static IExecutor sexecutor;


    @PostConstruct
    public void init() {
        ExecutorFactory.srcmsController = rcmsController;
        ExecutorFactory.srecoveryProcedureRepository = recoveryProcedureRepository;
        ExecutorFactory.srecoveryJobRepository = recoveryJobRepository;
        ExecutorFactory.srecoveryService = recoveryService;
        ExecutorFactory.sexecutor = executor;

        rcmsController.setRcmsStatusConsumer(rcmsStatusChangeConsumer);
    }

    private static Logger logger = LoggerFactory.getLogger(ExecutorFactory.class);

    public static IExecutor build(
            Function<RecoveryJob, FSMEvent> approvalConsumer,
            Function<RecoveryJob, FSMEvent> recoveryJobConsumer,
            BiConsumer<RecoveryProcedure, List<RecoveryEvent>> report,
            Supplier<FSMEvent> observer,
            Integer approvalTimeout,
            Integer executionTimeout,
            Consumer<RecoveryProcedure> persistConsumer,
            Consumer<RecoveryProcedure> onUpdateConsumer,
            Supplier<Boolean> availabilityConsumer,
            Runnable interruptConsumer) {

        IFSMListener listener = FSMListener.builder()
                .persistResultsConsumer(persistConsumer)
                .onUpdateConsumer(onUpdateConsumer)
                .build();


        FSM fsm = FSM.builder().listener(listener).build().initialize();
        IExecutor executor = Executor.builder()
                .fsm(fsm)
                .listener(listener)
                .executorService(Executors.newFixedThreadPool(1))
                .jobApprovalConsumer(approvalConsumer)
                .jobConsumer(recoveryJobConsumer)
                .statusReportConsumer(report)
                .observationConsumer(observer)
                .approvalTimeout(approvalTimeout)
                .executionTimeout(executionTimeout)
                .interruptConsumer(interruptConsumer)
                .isBusyConsumer(availabilityConsumer)
                .build();
        ((FSMListener) listener).setExecutor(executor);


        return executor;
    }

    public static Function<RecoveryJob, FSMEvent> automaticApprovalConsumer = recoveryJob -> {

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

        logger.info("Observing the system");
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        if (Math.random() < 0.3) {
//            logger.info("Received finished signal");
//            return FSMEvent.Finished;
//        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.info("Timeout of observation");
        return FSMEvent.NoEffect;

    };

    public static Consumer<String> rcmsStatusChangeConsumer = status -> {

        logger.info("Consuming new RCMS status: " + status);
        sexecutor.rcmsStatusUpdate(status);
    };

    public static Consumer<RecoveryProcedure> persistResultsConsumer = recoveryProcedure -> {

        logger.info("Updating recovery procedure " + recoveryProcedure.getId());
        for(RecoveryJob job: recoveryProcedure.getExecutedJobs()){
            srecoveryJobRepository.save(job);
        }
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


    public static BiConsumer<RecoveryProcedure, List<RecoveryEvent>> report = (rp, s) -> {
        logger.info("Reporting the acceptanceDecision: " + s);
        rp.setEventSummary(s);
    };

    public static IExecutor DEFAULT_EXECUTOR = build(
            manualApprovalConsumer,
            recoveryJobConsumer,
            report,
            fixedDelayObserver,
            1200, //TODO: investigate, never timeouts
            600,
            persistResultsConsumer,
            onProcedureUpdateConsumer,
            isAvailableSupplier,
            interruptConsumer

    );

    public static IExecutor FAKE_EXECUTOR = build(
            manualApprovalConsumer,
            recoveryJobFakeConsumer,
            report,
            fixedDelayObserver,
            60,
            600,
            persistResultsConsumer,
            onProcedureUpdateConsumer,
            fakeIsAvailableSupplier,
            interruptFakeConsumer

    );


}
