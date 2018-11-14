package ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm;

import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Builder
public class FSM {

    private State state;

    private final IFSMListener listener;

    public State getState() {
        return this.state;
    }

    private static final Logger logger = LoggerFactory.getLogger(FSM.class);

    public void transition(FSMEvent fsmEvent) {

        logger.info("Transition " + state + " + " + fsmEvent);

        if (fsmEvent == null) {
            throw new IllegalArgumentException();
        }

        int stateId = this.state.ordinal();
        int eventId = fsmEvent.ordinal();

        if (stateId >= State.values().length) {
            throw new IllegalStateException();
        }
        if (eventId >= fsmEvent.values().length) {
            throw new IllegalStateException();
        }

        State next = transition[stateId][eventId];

        if (next != null) {
            this.state = next;

            if (listener != null) {
                handleEvent(fsmEvent);
            }

            return;
        } else {
            throw new IllegalStateException("Illegal state transition: " + fsmEvent  + " on state: " + this.state);
        }

    }


    public FSM initialize() {

        this.state = State.Idle;

        int stateCount = State.values().length;
        int eventCount = FSMEvent.values().length;

        this.transition = new State[stateCount][eventCount];


        addToArray(State.Idle, FSMEvent.RecoveryStarts, State.SelectingJob);

        addToArray(State.SelectingJob, FSMEvent.NextJobNotFound, State.Failed);
        addToArray(State.SelectingJob, FSMEvent.NextJobFound, State.AwaitingApproval);
        addToArray(State.SelectingJob, FSMEvent.FinishedByItself, State.Cancelled);

        addToArray(State.AwaitingApproval, FSMEvent.JobAccepted, State.Recovering);
        addToArray(State.AwaitingApproval, FSMEvent.Timeout, State.Cancelled);
        addToArray(State.AwaitingApproval, FSMEvent.FinishedByItself, State.Cancelled);

        addToArray(State.Recovering, FSMEvent.JobCompleted, State.Observe);
        addToArray(State.Recovering, FSMEvent.Timeout, State.Failed);
        addToArray(State.Recovering, FSMEvent.Exception, State.Failed);
        addToArray(State.Recovering, FSMEvent.JobException, State.SelectingJob);

        addToArray(State.Observe, FSMEvent.NoEffect, State.SelectingJob);
        addToArray(State.Observe, FSMEvent.Finished, State.Completed);

        addToArray(State.Failed, FSMEvent.ReportStatus, State.Idle);
        addToArray(State.Cancelled, FSMEvent.ReportStatus, State.Idle);
        addToArray(State.Completed, FSMEvent.ReportStatus, State.Idle);

        /* From every state - instead of Idle, interrupt will bring FSM to Cancelled */
        for (State state : State.values()) {
            if (state != State.Idle)
                addToArray(state, FSMEvent.Interrupt, State.Cancelled);
        }
        return this;

    }


    /**
     * TODO: Refactor this: right now this handlers are both based on event and on state. Make it based on
     * state(initing-event)
     *
     * @param FSMEvent
     */
    private void handleEvent(FSMEvent FSMEvent) {

        FSMEvent result = null;

        switch (FSMEvent) {
            case RecoveryStarts:
                result = listener.onStart();
                break;
            case JobAccepted:
                result = listener.onJobAccepted();
                break;
            case JobCompleted:
                result = listener.onJobCompleted();
                break;
            case NoEffect:
                result = listener.onJobNoEffect();
                break;
            case RecoveryFailed:
                result = listener.onRecoveryFailed();
                break;
            case ReportStatus:
                result = listener.onReportStatus();
                break;
            case NextJobNotFound:
                result = listener.onNextJobNotFound();
                break;
            case NextJobFound:
                result = listener.onNextJobFound();
                break;
            case Timeout:
                result = listener.onTimeout();
                break;
            case Exception:
                result = listener.onException();
                break;
            case JobException:
                result = listener.onJobException();
                break;
            case Finished:
                result = listener.onFinished();
                break;
            case Interrupt:
                result = listener.onInterrupted();
                break;
            case FinishedByItself:
                result = listener.onCancelled();
        }

        if (result != null) {
            transition(result);
        }
    }

    private void addToArray(State onState, FSMEvent FSMEvent, State targetState) {

        transition[onState.ordinal()][FSMEvent.ordinal()] = targetState;
    }

    /**
     * Table of transitions state + signal = new state
     */
    private State transition[][];


}

