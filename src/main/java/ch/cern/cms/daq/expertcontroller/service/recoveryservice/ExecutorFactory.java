package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMEvent;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSM;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMListener;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.IFSMListener;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ExecutorFactory {

    public static IExecutor build(
            Function<RecoveryJob, FSMEvent> approvalConsumer,
            Function<RecoveryJob, FSMEvent> recoveryJobConsumer,
            BiConsumer<RecoveryProcedure, List<String>> report,
            Supplier<FSMEvent> observer,
            Integer approvalTimeout,
            Integer executionTimeout) {

        IFSMListener listener = FSMListener.builder().build();

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
                .build();
        ((FSMListener) listener).setExecutor(executor);


        return executor;
    }


}
