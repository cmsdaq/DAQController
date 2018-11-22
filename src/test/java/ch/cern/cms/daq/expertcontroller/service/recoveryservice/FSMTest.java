package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSMEvent;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.FSM;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.State;
import org.junit.Assert;
import org.junit.Test;

public class FSMTest {

    @Test
    public void singleJobTest() {

        FSM fsm = FSM.builder().listener(new IFSMListenerMock()).build().initialize();

        Assert.assertEquals(State.Idle, fsm.getState());

        fsm.transition(FSMEvent.RecoveryStarts);
        Assert.assertEquals(State.SelectingJob, fsm.getState());

        fsm.transition(FSMEvent.NextJobFound);
        Assert.assertEquals(State.AwaitingApproval, fsm.getState());

        fsm.transition(FSMEvent.JobAccepted);
        Assert.assertEquals(State.Recovering, fsm.getState());

        fsm.transition(FSMEvent.JobCompleted);
        Assert.assertEquals(State.Observe, fsm.getState());

        fsm.transition(FSMEvent.Finished);
        Assert.assertEquals(State.Completed, fsm.getState());

        fsm.transition(FSMEvent.ReportStatus);
        Assert.assertEquals(State.Idle, fsm.getState());

    }

    @Test
    public void multipleJobsTest() {

        FSM fsm = FSM.builder().listener(new IFSMListenerMock()).build().initialize();

        Assert.assertEquals(State.Idle, fsm.getState());

        fsm.transition(FSMEvent.RecoveryStarts);
        Assert.assertEquals(State.SelectingJob, fsm.getState());

        fsm.transition(FSMEvent.NextJobFound);
        Assert.assertEquals(State.AwaitingApproval, fsm.getState());

        fsm.transition(FSMEvent.JobAccepted);
        Assert.assertEquals(State.Recovering, fsm.getState());

        fsm.transition(FSMEvent.JobCompleted);
        Assert.assertEquals(State.Observe, fsm.getState());

        fsm.transition(FSMEvent.NoEffect);
        Assert.assertEquals(State.SelectingJob, fsm.getState());

        fsm.transition(FSMEvent.NextJobFound);
        Assert.assertEquals(State.AwaitingApproval, fsm.getState());

        fsm.transition(FSMEvent.JobAccepted);
        Assert.assertEquals(State.Recovering, fsm.getState());

        fsm.transition(FSMEvent.JobCompleted);
        Assert.assertEquals(State.Observe, fsm.getState());

        fsm.transition(FSMEvent.Finished);
        Assert.assertEquals(State.Completed, fsm.getState());

        fsm.transition(FSMEvent.ReportStatus);
        Assert.assertEquals(State.Idle, fsm.getState());

    }

    @Test
    public void cancellationTest() {

        FSM fsm = FSM.builder().listener(new IFSMListenerMock()).build().initialize();

        Assert.assertEquals(State.Idle, fsm.getState());

        fsm.transition(FSMEvent.RecoveryStarts);
        Assert.assertEquals(State.SelectingJob, fsm.getState());

        fsm.transition(FSMEvent.NextJobFound);
        Assert.assertEquals(State.AwaitingApproval, fsm.getState());

        fsm.transition(FSMEvent.JobAccepted);
        Assert.assertEquals(State.Recovering, fsm.getState());

        fsm.transition(FSMEvent.Interrupt);
        Assert.assertEquals(State.Cancelled, fsm.getState());

    }

    @Test(expected = IllegalStateException.class)
    public void illegalTransitionTest() {

        FSM fsm = FSM.builder().listener(new IFSMListenerMock()).build().initialize();

        Assert.assertEquals(State.Idle, fsm.getState());

        fsm.transition(FSMEvent.JobAccepted);
        Assert.fail("No exception thrown");

    }

}