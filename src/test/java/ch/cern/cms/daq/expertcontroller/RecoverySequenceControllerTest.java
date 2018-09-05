package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.api.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecord;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecordRepository;
import ch.cern.cms.daq.expertcontroller.rcmsController.LV0AutomatorControlException;
import ch.cern.cms.daq.expertcontroller.rcmsController.LV0AutomatorController;
import org.junit.*;
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

    @Autowired
    RecoveryService recoveryService;

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("observe.period", "1000");
    }


    /**
     * Regular sequence of signals is tested here: start -> accept -> problem-finished -> stepCompleted -> end
     * In between signals the entries in repository are asserted
     */
    @Test
    public void reqularUseCaseTest() {

        System.out.println(recoverySequenceController);
        System.out.println(recoveryService);

        // check initial state of repository
        Date start = new Date();
        List<RecoveryRecord> all = recoveryRecordRepository.findAll();
        System.out.println(all);
        assertEquals(0, all.size());

        // step 1: issue a recovery and check repository
        recoverySequenceController.start(genereateRecoveryRequest(1L));
        recoveryService.getOngoingProblems().add(1L);


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


        // step 2: accept the recovery and check the repository
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


        // step 3: signal from RCMS - recovery completed
        recoverySequenceController.stepCompleted(1L);
        all = recoveryRecordRepository.findAll();
        System.out.println(all);
        assertEquals(4, all.size());

        assertThat("Main recovery still not finished",all, hasItem(allOf(
                hasProperty("name", startsWith("Recovery of")),
                hasProperty("end", nullValue()))
        ));

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

        // step 4: signal from expert - problem finished
        recoveryService.finished(1L);

        // step 5: signal from self (see observe period) - no more actions needed
        // recoverySequenceController.end(); - do not call this explicitely - this will be called after observe period ends.
        // This will happen automatically after 1 sec ( see RecoverySequenceControllerMock)
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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


    /**
     * Regular sequence of signals is tested here: start -> accept -> stepCompleted -> end
     * In between signals the entries in repository are asserted
     */
    @Test
    public void problemFinishesSignalNeverReceivedFromTheExpertTest() {

        // check initial state of repository
        Date start = new Date();
        List<RecoveryRecord> all = recoveryRecordRepository.findAll();
        assertEquals(0, all.size());

        // step 1: issue a recovery and check repository
        recoverySequenceController.start(genereateRecoveryRequest(2L));
        recoveryService.getOngoingProblems().add(2L);


        all = recoveryRecordRepository.findAll();
        assertEquals(2, all.size());
        Date end = new Date();

        all = recoveryRecordRepository.findBetween(start, end);
        assertEquals(2, all.size());


        // step 2: accept the recovery and check the repository
        recoverySequenceController.accept(1910902,"");
        all = recoveryRecordRepository.findAll();
        assertEquals(3, all.size());


        // step 3: signal from RCMS - recovery completed
        recoverySequenceController.stepCompleted(2L);
        all = recoveryRecordRepository.findAll();
        assertEquals(4, all.size());

        // step 4: signal from expert DOESN'T COME - problem still ongoing
        // recoveryService.finished(2L); - don't call this - just for demonstration what is missing in this test case

        // step 5: signal from self (see observe period) - no more actions needed
        // recoverySequenceController.end(); - do not call - just for demonstration what is missing in this test case
        // This will happen automatically after 1 sec ( see RecoverySequenceControllerMock)
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        all = recoveryRecordRepository.findAll();
        System.out.println(all);
        assertEquals(4, all.size());

        assertThat(all, contains(
                hasProperty("name", startsWith("Recovery of")),
                hasProperty("name", is("Waiting for approval")),
                hasProperty("name", startsWith("Executing ")),
                hasProperty("name", startsWith("Observing"))
        ));
        assertThat("Main recovery entry still ongoing",all, hasItem(allOf(
                hasProperty("name", startsWith("Recovery of")),
                hasProperty("start", notNullValue()),
                hasProperty("end", nullValue()))
        ));


        end = new Date();
        all = recoveryRecordRepository.findBetween(start, end);
        assertEquals(4, all.size());

    }

    @After
    public void beforeTest(){
        recoveryRecordRepository.deleteAllInBatch();
    }

    private RecoveryRequest genereateRecoveryRequest(Long id){

        RecoveryRequest rr = new RecoveryRequest();
        rr.setProblemId(id);
        return rr;

    }

    /**
     * This TestConfig will be loaded by spring in order to replace RecoveryService with it's mock version defined below
     */
    @TestConfiguration
    public static class TestConfig {
        @Bean
        public RecoveryService recoveryService() {
            return new RecoveryServiceMock();
        }

        @Bean
        public RecoverySequenceController recoverySequenceController(){
            return new RecoverySequenceControllerTest.TestConfig.RecoverySequenceControllerMock();
        }

        @Bean
        public LV0AutomatorController lv0AutomatorController() throws LV0AutomatorControlException {
            return new RecoverySequenceControllerTest.TestConfig.LV0AutomatorControllerMock(null);
        }

        public class RecoveryServiceMock extends RecoveryService {

            /**
             * This method of RecoveryService is overwritten in order to prevent from calling other system functions which are not a scope of this test
             */
            @Override
            public void endRecovery() {

            }

            @Override
            public void finished(Long id) {
                getOngoingProblems().remove(id);
            }

        }

        public class RecoverySequenceControllerMock extends RecoverySequenceController{
            public RecoverySequenceControllerMock(){
                observePeriod = 1000;
            }
        }

        public class LV0AutomatorControllerMock extends LV0AutomatorController{

            public LV0AutomatorControllerMock(String senderURI) throws LV0AutomatorControlException {
                super(senderURI);
            }

            @Override
            public void interruptRecovery() throws LV0AutomatorControlException {

            }
        }


    }
}