package ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm;

public enum State {

    Idle,
    SelectingJob,
    AwaitingApproval,
    Recovering,
    Observe,
    Failed,
    Cancelled,
    Completed
}
