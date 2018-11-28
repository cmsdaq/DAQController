package ch.cern.cms.daq.expertcontroller.service;

import org.junit.Assert;
import org.junit.Test;

public class LoopBreakerTest {

    @Test
    public void test() throws InterruptedException {


        LoopBreaker loopBreaker = new LoopBreaker();
        loopBreaker.numberOfExecutions = 2;
        loopBreaker.timeWindow = 2;


        // 1st request
        Assert.assertFalse(loopBreaker.exceedsTheLimit());
        loopBreaker.registerAccepted();
        Thread.sleep(10);

        // 2nd request
        Assert.assertFalse(loopBreaker.exceedsTheLimit());
        loopBreaker.registerAccepted();
        Thread.sleep(10);

        // 3rd request
        Assert.assertTrue(loopBreaker.exceedsTheLimit());

        Thread.sleep(2000);

        Assert.assertFalse(loopBreaker.exceedsTheLimit());
        loopBreaker.registerAccepted();
        Thread.sleep(10);

        Assert.assertFalse(loopBreaker.exceedsTheLimit());
        loopBreaker.registerAccepted();
        Thread.sleep(10);

        Assert.assertTrue(loopBreaker.exceedsTheLimit());

    }
}