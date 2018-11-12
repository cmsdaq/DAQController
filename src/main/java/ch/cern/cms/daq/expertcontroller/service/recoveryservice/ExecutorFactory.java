package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.entity.Event;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
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
import org.springframework.stereotype.Service;
import rcms.fm.fw.service.command.CommandServiceException;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class ExecutorFactory {

    @Autowired
    RcmsController rcmsController;

    protected static RcmsController srcmsController;

    @PostConstruct
    public void init(){
        logger.info("Setting upd RCMS controller: " + rcmsController);
        ExecutorFactory.srcmsController = rcmsController;
    }

    private static Logger logger = LoggerFactory.getLogger(ExecutorFactory.class);

    public static IExecutor build(
            Function<RecoveryJob, FSMEvent> approvalConsumer,
            Function<RecoveryJob, FSMEvent> recoveryJobConsumer,
            BiConsumer<RecoveryProcedure, List<String>> report,
            Supplier<FSMEvent> observer,
            Integer approvalTimeout,
            Integer executionTimeout) {

        IFSMListener listener = FSMListener.builder().build();

        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);

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
                .delayedFinishedSignals(pool)
                .build();
        ((FSMListener) listener).setExecutor(executor);


        return executor;
    }

    public static Function<RecoveryJob, FSMEvent> approvalConsumerThatAccepts = recoveryJob -> {

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.info("Approval required, accepting");
        return FSMEvent.JobAccepted;

    };

    public static Function<RecoveryJob, FSMEvent> recoveryJobConsumer = recoveryJob -> {
        try {
            logger.debug("Rcms controller: " + srcmsController);
            logger.info("Passing the recovery job: " + recoveryJob + " to RCMS controller");

            recoveryJob.setStart(OffsetDateTime.now());
            srcmsController.execute(recoveryJob);
            recoveryJob.setEnd(OffsetDateTime.now());
            return FSMEvent.JobCompleted;
        } catch (CommandServiceException | LV0AutomatorControlException e) {
            logger.warn("Job failed due to RCMS exception: "+ e.getMessage());
            logger.info("Recovery job considered to be failed");
            return FSMEvent.Exception;
        }
    };


    public static Supplier<FSMEvent> fixedDelayObserver = new Supplier<FSMEvent>() {


        @Override
        public FSMEvent get() {
            logger.info("Observing the system");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Timeout");
            return FSMEvent.NoEffect;
        }
    };


    public static BiConsumer<RecoveryProcedure, List<String>> report = (rp, s) -> {
        logger.info("Reporting the acceptanceDecision: " + s);
        rp.setEventSummary(s.stream().map(c -> Event.builder().content(c).build()).collect(Collectors.toList()));
    };

    public static IExecutor DEFAULT_EXECUTOR = build(
            approvalConsumerThatAccepts,
            recoveryJobConsumer,
            report,
            fixedDelayObserver,
            2,
            600
    );


}
