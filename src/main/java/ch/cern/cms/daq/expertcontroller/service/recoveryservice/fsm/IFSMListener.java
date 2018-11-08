package ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;

import java.util.List;

public interface IFSMListener {

    void setCurrentProcedure(RecoveryProcedure recoveryProcedure);

    /**
     * Get summary of actions
     */
    List<String> getSummary();

    FSMEvent onStart();

    FSMEvent onJobAccepted();

    FSMEvent onJobCompleted();

    FSMEvent onJobNoEffect();

    FSMEvent onRecoveryFailed();

    FSMEvent onReportStatus();

    FSMEvent onNextJobNotFound();

    FSMEvent onNextJobFound();

    FSMEvent onTimeout();

    FSMEvent onException();

    FSMEvent onFinished();

    FSMEvent onInterrupted();

    FSMEvent onCancelled();

    RecoveryJob getCurrentJob();

    RecoveryProcedure getCurrentProcedure();

}
