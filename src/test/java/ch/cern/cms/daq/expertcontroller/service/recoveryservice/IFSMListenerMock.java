package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.entity.Event;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMEvent;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.IFSMListener;

import java.util.List;

class IFSMListenerMock implements IFSMListener {

    @Override
    public void setCurrentProcedure(RecoveryProcedure recoveryProcedure) {
        return;
    }

    @Override
    public FSMEvent onStart() {
        System.out.println("on start");
        return null;
    }

    @Override
    public FSMEvent onJobAccepted() {
        System.out.println("on job accepted");
        return null;
    }

    @Override
    public FSMEvent onOtherJobAccepted() {
        return null;
    }

    @Override
    public FSMEvent onJobCompleted() {

        System.out.println("on job completed");
        return null;
    }

    @Override
    public FSMEvent onJobNoEffect() {
        System.out.println("on job no effect");
        return null;

    }

    @Override
    public FSMEvent onRecoveryFailed() {
        System.out.println("on recovery failed");
        return null;

    }

    @Override
    public FSMEvent onReportStatus() {
        System.out.println("on report acceptanceDecision");
        return null;

    }

    @Override
    public FSMEvent onNextJobNotFound() {
        System.out.println("on next job not found");
        return null;

    }

    @Override
    public FSMEvent onNextJobFound() {

        System.out.println("on next job found");
        return null;
    }

    @Override
    public FSMEvent onTimeout() {
        System.out.println("on timeout");
        return null;

    }

    @Override
    public FSMEvent onException() {
        System.out.println("on exception");
        return null;

    }

    @Override
    public FSMEvent onJobException() {
        System.out.println("on job exception");
        return null;
    }

    @Override
    public FSMEvent onFinished() {
        System.out.println("on finished");
        return null;

    }

    @Override
    public FSMEvent onInterrupted() {
        System.out.println("on interrupted");
        return null;
    }

    @Override
    public FSMEvent onCancelled() {
        System.out.println("on cancelled");
        return null;
    }

    @Override
    public FSMEvent onProcedureAccepted() {
        System.out.println("on procedure accepted");
        return null;
    }

    @Override
    public RecoveryJob getCurrentJob() {
        return null;
    }

    @Override
    public FSMEvent onApprovedJobNotExist() {
        return null;
    }

    @Override
    public void setCurrentJob(RecoveryJob job) {

    }

    @Override
    public RecoveryProcedure getCurrentProcedure() {
        return null;
    }

    @Override
    public void onNewRcmsStatus(String status) {
        System.out.println("New rcms status: " + status);
    }

    @Override
    public List<Event> getSummary() {
        return null;
    }
}