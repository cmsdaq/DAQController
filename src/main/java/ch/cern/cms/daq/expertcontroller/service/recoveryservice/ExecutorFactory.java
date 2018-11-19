package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalRequest;
import ch.cern.cms.daq.expertcontroller.entity.Event;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
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
    IRecoveryService recoveryService;

    protected static RcmsController srcmsController;
    protected static RecoveryProcedureRepository srecoveryProcedureRepository;
    protected static IRecoveryService srecoveryService;


    @PostConstruct
    public void init() {
        ExecutorFactory.srcmsController = rcmsController;
        ExecutorFactory.srecoveryProcedureRepository = recoveryProcedureRepository;
        ExecutorFactory.srecoveryService = recoveryService;
    }

    private static Logger logger = LoggerFactory.getLogger(ExecutorFactory.class);

    public static IExecutor build(
            Function<RecoveryJob, FSMEvent> approvalConsumer,
            Function<RecoveryJob, FSMEvent> recoveryJobConsumer,
            BiConsumer<RecoveryProcedure, List<Event>> report,
            Supplier<FSMEvent> observer,
            Integer approvalTimeout,
            Integer executionTimeout,
            Consumer<RecoveryProcedure> persistConsumer,
            Consumer<RecoveryProcedure> onUpdateConsumer,
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

    public static Consumer<RecoveryProcedure> persistResultsConsumer = recoveryProcedure -> {

        logger.info("Updating recovery procedure " + recoveryProcedure.getId());
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


    public static BiConsumer<RecoveryProcedure, List<Event>> report = (rp, s) -> {
        logger.info("Reporting the acceptanceDecision: " + s);
        rp.setEventSummary(s);
    };

    public static IExecutor DEFAULT_EXECUTOR = build(
            manualApprovalConsumer,
            recoveryJobConsumer,
            report,
            fixedDelayObserver,
            2,
            600,
            persistResultsConsumer,
            onProcedureUpdateConsumer,
            interruptConsumer

    );


}
