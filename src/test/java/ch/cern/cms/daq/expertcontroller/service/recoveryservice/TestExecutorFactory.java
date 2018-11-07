package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class TestExecutorFactory extends ExecutorFactory {


    static Logger logger = LoggerFactory.getLogger(TestExecutorFactory.class);


    public static Function<RecoveryJob, FSMEvent> approvalConsumerThatAccepts = recoveryJob -> {
        logger.info("Approval required, accepting");
        return FSMEvent.JobAccepted;

    };


    public static Function<RecoveryJob, FSMEvent> approvalConsumerThatRejects = recoveryJob -> {
        logger.info("Approval required, rejecting/timeouts");
        return FSMEvent.Timeout;
    };

    public static Function<RecoveryJob, FSMEvent> approvalConsumerThatNeverAccepts = recoveryJob -> null;

    public static Function<RecoveryJob, FSMEvent> recoveryJobConsumerThatCompletes = recoveryJob -> {
        logger.info("Executing job: " + recoveryJob.getJob());
        try {
            Thread.sleep(100);
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

    public static Supplier<FSMEvent> observerThatTimeouts = new Supplier<FSMEvent>() {

        boolean firstPassed = false;

        @Override
        public FSMEvent get() {
            logger.info("Observing the system");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (firstPassed) {
                logger.info("Finished successfully");
                return FSMEvent.Finished;

            }
            logger.info("Timeout");
            firstPassed = true;
            return FSMEvent.NoEffect;
        }
    };

    public static BiConsumer<RecoveryProcedure, List<String>> report = (rp, s) ->
            logger.info("Reporting the acceptanceDecision: " + s);


    public static IExecutor TEST_EXECUTOR = build(
            approvalConsumerThatNeverAccepts,
            recoveryJobConsumerThatCompletes,
            report, observerThatTimeouts,
            1,
            1
    );


}