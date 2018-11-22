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

    ProcedureAccepted,

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

    /**
     * If operator accepts other step than recommended and it doesn't exist
     */
    AcceptedJobDoesNotExist,

    Timeout,

    Exception,


    JobException,


    /**
     * Problem that has been a reason to recover has finished due to controller recovery
     */
    Finished,

    /**
     * Problem that has been a reason to recover has finished by itself
     */
    FinishedByItself,

    Interrupt

}
