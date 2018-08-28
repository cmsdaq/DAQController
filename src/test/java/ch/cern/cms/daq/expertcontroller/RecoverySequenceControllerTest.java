package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.api.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.api.RecoveryRequestStep;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecord;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecordRepository;
import ch.cern.cms.daq.expertcontroller.rcmsController.LV0AutomatorControlException;
import ch.cern.cms.daq.expertcontroller.rcmsController.RcmsController;
import org.h2.tools.Recover;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.junit.Assert.assertEquals;

/**
 * In this test the methods of RecoverySequenceController:
 * - start
 * - accept
 * - stepCompleted
 * - end
 * are called and corresponding response of the system is tested.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class RecoverySequenceControllerTest {

    @Autowired
    RecoverySequenceController recoverySequenceController;

    @Autowired
    RecoveryRecordRepository recoveryRecordRepository;

    @Test
    public void test() {

        Date start = new Date();

        List<RecoveryRecord> all = recoveryRecordRepository.findAll();
        System.out.println(all);
        assertEquals(0, all.size());

        recoverySequenceController.start(genereateRecoveryRequest(1L));
        all = recoveryRecordRepository.findAll();
        System.out.println(all);
        assertEquals(2, all.size());
        assertThat(all, hasItem(allOf(
                hasProperty("name", is("Waiting for approval")),
                hasProperty("start", notNullValue()),
                hasProperty("end", nullValue()))
        ));

        assertThat(all, hasItem(allOf(
                hasProperty("name", startsWith("Recovery of")),
                hasProperty("start", notNullValue()),
                hasProperty("end", nullValue()))
        ));

        Date end = new Date();

        all = recoveryRecordRepository.findBetween(start, end);
        System.out.println("Queried: "+all);
        assertEquals(2, all.size());


        recoverySequenceController.accept(1910902,"");
        all = recoveryRecordRepository.findAll();
        System.out.println(all);
        assertEquals(3, all.size());
        assertThat(all, contains(
                hasProperty("name", startsWith("Recovery of")),
                hasProperty("name", is("Waiting for approval")),
                hasProperty("name", startsWith("Executing "))
        ));
        assertThat(all, hasItem(allOf(
                hasProperty("name", is("Waiting for approval")),
                hasProperty("start", notNullValue()),
                hasProperty("end", notNullValue()))
        ));

        recoverySequenceController.stepCompleted();
        all = recoveryRecordRepository.findAll();
        System.out.println(all);
        assertEquals(4, all.size());
        assertThat(all, contains(
                hasProperty("name", startsWith("Recovery of")),
                hasProperty("name", is("Waiting for approval")),
                hasProperty("name", startsWith("Executing ")),
                hasProperty("name", startsWith("Observing"))
        ));

        assertThat(all, hasItem(allOf(
                hasProperty("name", startsWith("Executing ")),
                hasProperty("start", notNullValue()),
                hasProperty("end", notNullValue()))
        ));


        recoverySequenceController.end();
        all = recoveryRecordRepository.findAll();
        System.out.println(all);
        assertEquals(4, all.size());

        assertThat(all, contains(
                hasProperty("name", startsWith("Recovery of")),
                hasProperty("name", is("Waiting for approval")),
                hasProperty("name", startsWith("Executing ")),
                hasProperty("name", startsWith("Observing"))
        ));
        assertThat(all, hasItem(allOf(
                hasProperty("name", startsWith("Recovery of")),
                hasProperty("start", notNullValue()),
                hasProperty("end", notNullValue()))
        ));


        end = new Date();
        all = recoveryRecordRepository.findBetween(start, end);
        assertEquals(4, all.size());

    }

    private RecoveryRequest genereateRecoveryRequest(Long id){

        RecoveryRequest rr = new RecoveryRequest();
        rr.setProblemId(id);
        return rr;

    }

    @TestConfiguration
    public static class TestConfig {
        @Bean
        public RecoveryManager recoveryManager() {
            return new RecoverySequenceControllerTest.TestConfig.RecoveryManagerMock();
        }

        public class RecoveryManagerMock extends RecoveryManager {


            @Override
            public void endRecovery() {

            }
        }


    }
}