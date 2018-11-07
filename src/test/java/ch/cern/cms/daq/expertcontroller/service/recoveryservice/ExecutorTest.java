package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.*;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ExecutorTest {

    FSM fsm;
    IExecutor executor;

    private final static Logger logger = LoggerFactory.getLogger(ExecutorTest.class);

    @Test
    public void emptyProcedureTest() {

        prepare(approvalConsumerThatAccepts, recoveryJobConsumerThatCompletes, observerThatFinishes);

        List<RecoveryJob> list = new ArrayList<>();
        RecoveryProcedure job = RecoveryProcedure.builder().procedure(list).build();
        List<String> result = executor.start(job);
        Assert.assertEquals(State.Idle, fsm.getState());
        Assert.assertEquals(Arrays.asList("Job not found, recovery failed"), result);
    }

    @Test
    public void simpleProcedureTest() {
        prepare(approvalConsumerThatAccepts, recoveryJobConsumerThatCompletes, observerThatFinishes);

        List<RecoveryJob> list = new ArrayList<>();
        list.add(RecoveryJob.builder().job("J1").build());
        RecoveryProcedure job = RecoveryProcedure.builder().procedure(list).build();
        List<String> result = executor.start(job);
        Assert.assertEquals(State.Idle, fsm.getState());
        Assert.assertEquals(Arrays.asList("Job J1 accepted",
                                          "Job J1 completed",
                                          "Recovery procedure finished successfully"
        ), result);
    }

    @Test
    public void multipleStepProcedureTest() {
        prepare(approvalConsumerThatAccepts, recoveryJobConsumerThatCompletes, observerThatTimeouts);

        List<RecoveryJob> list = Arrays.asList(
                RecoveryJob.builder().job("J1").build(),
                RecoveryJob.builder().job("J2").build());
        RecoveryProcedure job = RecoveryProcedure.builder().procedure(list).build();
        List<String> result = executor.start(job);
        Assert.assertEquals(State.Idle, fsm.getState());

        Assert.assertEquals(Arrays.asList("Job J1 accepted",
                                          "Job J1 completed",
                                          "Job J1 didn't fix the problem",
                                          "Job J2 accepted",
                                          "Job J2 completed",
                                          "Recovery procedure finished successfully"
        ), result);

    }

    @Test
    public void recoveryJobThatTimesOutTest() {
        prepare(approvalConsumerThatAccepts, recoveryJobConsumerThatHangs, observerThatFinishes);
        List<RecoveryJob> list = new ArrayList<>();
        list.add(RecoveryJob.builder().job("VLJ").build());
        RecoveryProcedure job = RecoveryProcedure.builder().procedure(list).build();
        List<String> result = executor.start(job);
        Assert.assertEquals(State.Idle, fsm.getState());
        Assert.assertEquals(Arrays.asList("Job VLJ accepted",
                                          "Job: VLJ times out"), result);
    }

    @Test
    public void recoveryJobCancelledFromOutsideTest() throws InterruptedException {
        prepare(approvalConsumerThatAccepts, recoveryJobConsumerThatHangs, observerThatFinishes);
        List<RecoveryJob> list = new ArrayList<>();
        list.add(RecoveryJob.builder().job("Very long job").build());
        RecoveryProcedure job = RecoveryProcedure.builder().procedure(list).build();

        (new Thread() {
            public void run() {
                List<String> result = executor.start(job);
            }
        }).start();

        Thread.sleep(100);

        executor.interrupt();

        Thread.sleep(100);


        Assert.assertEquals(State.Idle, fsm.getState());
        //Assert.assertEquals(Arrays.asList("Job not found, recovery failed"), result);
    }

    @Test
    public void requestRecoveryStatusFromOutsideTest() throws InterruptedException {
        prepare(approvalConsumerThatAccepts, recoveryJobConsumerThatCompletes, observerThatFinishes);

        List<RecoveryJob> list = new ArrayList<>();
        list.add(RecoveryJob.builder().job("J").build());
        RecoveryProcedure job = RecoveryProcedure.builder().procedure(list).build();


        ExecutorStatus status = executor.getStatus();

        Assert.assertEquals(
                ExecutorStatus.builder()
                        .state(State.Idle)
                        .actionSummary(null).build()
                , status);

        new Thread(() -> executor.start(job)).start();

        Thread.sleep(50); // Half the time to execute the job

        status = executor.getStatus();

        Assert.assertEquals(
                ExecutorStatus.builder()
                        .state(State.Recovering)
                        .actionSummary(Arrays.asList("Job J accepted")).build()
                , status);

        Thread.sleep(100);

        status = executor.getStatus();

        Assert.assertEquals(
                ExecutorStatus.builder()
                        .state(State.Observe)
                        .actionSummary(Arrays.asList(
                                "Job J accepted",
                                "Job J completed"
                        )).build()
                , status);

        Thread.sleep(100);

        status = executor.getStatus();

        Assert.assertEquals(
                ExecutorStatus.builder()
                        .state(State.Idle)
                        .actionSummary(Arrays.asList(
                                "Job J accepted",
                                "Job J completed",
                                "Recovery procedure finished successfully"
                        )).build()
                , status);


    }

    private void prepare(Function<RecoveryJob, FSMEvent> approvalConsumer,
                         Function<RecoveryJob, FSMEvent> recoveryJobConsumer,
                         Supplier<FSMEvent> observer) {

        IFSMListener listener = FSMListener.builder().build();

        fsm = FSM.builder().listener(listener).build().initialize();
        executor = Executor.builder()
                .fsm(fsm)
                .listener(listener)
                .executorService(Executors.newFixedThreadPool(1))
                .jobApprovalConsumer(approvalConsumer)
                .jobConsumer(recoveryJobConsumer)
                .statusReportConsumer(report)
                .observationConsumer(observer)
                .approvalTimeout(1)
                .executionTimeout(1)
                .build();
        ((FSMListener) listener).setExecutor(executor);

    }

    Function<RecoveryJob, FSMEvent> approvalConsumerThatAccepts = new Function<RecoveryJob, FSMEvent>() {
        @Override
        public FSMEvent apply(RecoveryJob recoveryJob) {
            logger.info("Approval required, accepting");
            return FSMEvent.JobAccepted;
        }
    };


    Function<RecoveryJob, FSMEvent> approvalConsumerThatRejects = new Function<RecoveryJob, FSMEvent>() {
        @Override
        public FSMEvent apply(RecoveryJob recoveryJob) {
            logger.info("Approval required, rejecting/timeouts");
            return FSMEvent.Timeout;
        }
    };

    Function<RecoveryJob, FSMEvent> recoveryJobConsumerThatCompletes = new Function<RecoveryJob, FSMEvent>() {
        @Override
        public FSMEvent apply(RecoveryJob recoveryJob) {
            logger.info("Executing job: " + recoveryJob.getJob());
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return FSMEvent.JobCompleted;
        }
    };

    Function<RecoveryJob, FSMEvent> recoveryJobConsumerThatHangs = new Function<RecoveryJob, FSMEvent>() {
        @Override
        public FSMEvent apply(RecoveryJob recoveryJob) {
            logger.info("Executing job: " + recoveryJob.getJob());
            try {
                Thread.sleep(100000);
            } catch (InterruptedException e) {
            }

            return FSMEvent.JobCompleted;
        }
    };

    Function<RecoveryJob, FSMEvent> recoveryJobConsumerThatThrowsException = new Function<RecoveryJob, FSMEvent>() {
        @Override
        public FSMEvent apply(RecoveryJob recoveryJob) {
            logger.info("Executing job: " + recoveryJob.getJob());
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Exception catched");
            return FSMEvent.Exception;
        }
    };

    Supplier<FSMEvent> observerThatFinishes = new Supplier<FSMEvent>() {
        @Override
        public FSMEvent get() {
            logger.info("Observing the system");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Condition finished");
            return FSMEvent.Finished;
        }
    };

    Supplier<FSMEvent> observerThatTimeouts = new Supplier<FSMEvent>() {

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

    BiConsumer<RecoveryProcedure, List<String>> report = (rp, s) ->
            logger.info("Reporting the acceptanceDecision: " + s);

}
