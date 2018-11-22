package ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm;

import ch.cern.cms.daq.expertcontroller.entity.Event;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;

import java.util.List;

public interface IFSMListener {

    void setCurrentProcedure(RecoveryProcedure recoveryProcedure);

    /**
     * Get summary of actions
     */
    List<Event> getSummary();

    FSMEvent onStart();

    FSMEvent onJobAccepted();

    FSMEvent onOtherJobAccepted();

    FSMEvent onJobCompleted();

    FSMEvent onJobNoEffect();

    FSMEvent onRecoveryFailed();

    FSMEvent onReportStatus();

    FSMEvent onNextJobNotFound();

    FSMEvent onNextJobFound();

    FSMEvent onTimeout();

    FSMEvent onException();

    FSMEvent onJobException();

    FSMEvent onFinished();

    FSMEvent onInterrupted();

    FSMEvent onCancelled();

    FSMEvent onProcedureAccepted();

    RecoveryJob getCurrentJob();

    FSMEvent onApprovedJobNotExist();

    void setCurrentJob(RecoveryJob job);

    RecoveryProcedure getCurrentProcedure();

    void onNewRcmsStatus(String status);

}
