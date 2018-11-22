package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryEvent;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class TestExecutorFactory extends ExecutorFactory {


    static Logger logger = LoggerFactory.getLogger(TestExecutorFactory.class);

    public static Integer observingTime = 1000;

    public static Integer recoveringTime = 1000;


    public static Function<RecoveryJob, FSMEvent> approvalConsumerThatAccepts = recoveryJob -> {
        logger.info("Approval required, accepting");
        return FSMEvent.JobAccepted;

    };


    public static Function<RecoveryJob, FSMEvent> approvalConsumerThatRejects = recoveryJob -> {
        logger.info("Approval required, rejecting/timeouts");
        return FSMEvent.Timeout;
    };

    public static Function<RecoveryJob, FSMEvent> approvalConsumerThatNeverAccepts = recoveryJob -> null;

    public static Function<RecoveryJob, FSMEvent> recoveryJobConsumerThatCompletesImmediately = recoveryJob -> {
        logger.info("Executing job: " + recoveryJob.getJob());
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return FSMEvent.JobCompleted;

    };

    public static Function<RecoveryJob, FSMEvent> fixedTimeRecoveryJobConsumer = recoveryJob -> {
        logger.info("Executing job: " + recoveryJob.getJob());
        try {
            Thread.sleep(recoveringTime);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return FSMEvent.JobCompleted;

    };

    public static Function<RecoveryJob, FSMEvent> recoveryJobConsumerThatHangs = recoveryJob -> {
        logger.info("Executing job: " + recoveryJob.getJob());
        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
        }

        return FSMEvent.JobCompleted;

    };

    public static Function<RecoveryJob, FSMEvent> recoveryJobConsumerThatThrowsException = recoveryJob -> {
        logger.info("Executing job: " + recoveryJob.getJob());
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.info("Exception catched");
        return FSMEvent.Exception;

    };

    public static Supplier<FSMEvent> observerThatFinishes = () -> {
        logger.info("Observing the system");
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.info("Condition finished");
        return FSMEvent.Finished;

    };

    public static Supplier<FSMEvent> observerThatTimeoutsImmediately = new Supplier<FSMEvent>() {


        @Override
        public FSMEvent get() {
            logger.info("Observing the system");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Timeout");
            return FSMEvent.NoEffect;
        }
    };

    public static Supplier<FSMEvent> fixedDelayObserver = new Supplier<FSMEvent>() {


        @Override
        public FSMEvent get() {
            logger.info("Observing the system");
            try {
                Thread.sleep(observingTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Timeout");
            return FSMEvent.NoEffect;
        }
    };

    public static BiConsumer<RecoveryProcedure, List<RecoveryEvent>> report = (rp, s) -> {
        logger.info("Reporting the acceptanceDecision: " + s);
        rp.setEventSummary(s);
    };

    public static Consumer<RecoveryProcedure> printRecoveryProcedurePersistor = recoveryProcedure ->
            logger.info("Persist " + recoveryProcedure.getProblemTitle());


    public static Consumer<RecoveryProcedure> printOnUpdateConsumer = recoveryProcedure ->
            logger.info("On update:  " + recoveryProcedure.getProblemTitle());


    public static Runnable interruptConsumer = () -> {
        logger.info("Interrupting rcms job");

    };

    public static Supplier<Boolean> isAvailableSupplier = () -> true;

    public static IExecutor TEST_EXECUTOR = build(
            approvalConsumerThatNeverAccepts,
            recoveryJobConsumerThatCompletesImmediately,
            report, observerThatTimeoutsImmediately,
            1,
            1,
            printRecoveryProcedurePersistor,
            printOnUpdateConsumer,
            isAvailableSupplier,
            interruptConsumer
    );

    public static IExecutor INTEGRATION_TEST_EXECUTOR = build(
            approvalConsumerThatNeverAccepts,
            fixedTimeRecoveryJobConsumer,
            report,
            fixedDelayObserver,
            1,
            recoveringTime * 2,
            persistResultsConsumer,
            printOnUpdateConsumer,
            isAvailableSupplier,
            interruptConsumer
    );

}