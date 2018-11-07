package ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm;

public enum FSMEvent {

    /**
     * Recovery procedure starts
     */
    RecoveryStarts,

    /**
     * Job has been approved
     */
    JobAccepted,

    /**
     * Job has been rejected
     */
    JobRejected,

    /**
     * Other job has been accepted
     */
    OtherJobAccepted,

    /**
     * Job has completed - RCMS has completed the job
     */
    JobCompleted,


    /**
     *
     */
    NoEffect,

    /**
     *
     */
    RecoveryFailed,

    /**
     * Report procedure acceptanceDecision
     */
    ReportStatus,

    NextJobNotFound,

    NextJobFound,

    Timeout,

    Exception,

    Finished,

    Interrupt

}
