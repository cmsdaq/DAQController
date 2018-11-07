package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalRequest;
import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalResponse;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRequestStep;
import ch.cern.cms.daq.expertcontroller.service.IRecoveryService;
import ch.cern.cms.daq.expertcontroller.service.rcms.LV0AutomatorControlException;
import ch.cern.cms.daq.expertcontroller.service.rcms.RcmsController;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import javax.xml.bind.DatatypeConverter;
import java.lang.reflect.Type;
import java.util.*;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.post;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.Matchers.*;


//TODO: add oneRequestTEset when condition finishes itself, add oneRequestTEset when interrupt from LV0
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Ignore
//TODO: get inspiration from this tests
public class ExpertControllerApplicationTests {


    @Value("${observe.period}")
    private Long observePeriod;

    private static final Long timeToExecute = 500L;

    /* Queue name for clients to subscribe to get approve requests */
    private static final String SUBSCRIBE_REQUEST = "/topic/approveRequests";

    /* Endpoint name for clients to send approve responses */
    private static final String SEND_APPROVE = "/app/approve";
    /* Default header for json requests */
    private final Header jsonHeader = new Header("Content-Type", "application/json");
    Queue<ApprovalRequest> approvalRequests;
    /* Web socket session of the client who approves the requests */
    StompSession stompSession;
    @Autowired
    private RcmsController rcmsController;

    @Autowired
    private IRecoveryService recoveryService;


    @BeforeClass
    public static void beforeClass() {
        System.setProperty("observe.period", "1000");
    }

    @LocalServerPort
    private int port;

    private List<Transport> createTransportClient() {
        List<Transport> transports = new ArrayList<>(1);
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        return transports;
    }

    @Before
    public void setUp() throws Exception {
        approvalRequests = new LinkedList<>();

        RestAssured.port = port;
        WebSocketStompClient stompClient = new WebSocketStompClient(new SockJsClient(createTransportClient()));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String URL = "ws://localhost:" + port + "/recovery";
        stompSession = stompClient.connect(URL, new StompSessionHandlerAdapter() {
        }).get(1, SECONDS);

        stompSession.subscribe(SUBSCRIBE_REQUEST, new DefaultStompFrameHandler());

        //recoveryService.getOngoingProblems().clear();

        //Thread.sleep(15000);

    }

    @Test
    public void unsupportedMediaType() {
        post("/recover").then().assertThat().statusCode(equalTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()));
    }

    @Test
    public void emptyRequest() {
        given().header(jsonHeader).post("/recover").then().assertThat().statusCode(equalTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    public void missingRecoverySteps() {

        RecoveryRequest r = new RecoveryRequest();
        r.setProblemId(10L);
        given().header(jsonHeader).body(r).post("/recover").then().assertThat().statusCode(equalTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    public void emptyRecoverySteps() {

        RecoveryRequest r = new RecoveryRequest();
        r.setProblemId(10L);
        r.setRecoverySteps(new ArrayList<>());
        given().header(jsonHeader).body(r).post("/recover").then().assertThat().statusCode(equalTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    public void simpleRequest() throws InterruptedException {


        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 2);
        String end = DatatypeConverter.printDateTime(cal);
        cal.add(Calendar.YEAR, -1);
        String start = DatatypeConverter.printDateTime(cal);

        //System.out.println("All: " + recoveryRecordRepository.findAll());

        System.out.println("Requesting: " + start + "-" + end);
        RecoveryRequest r = RecoveryRequest.builder().problemId(10L).recoverySteps(new ArrayList<>()).build();
        r.getRecoverySteps().add(RecoveryRequestStep.builder().build());
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryId", equalTo(1));

        given().queryParam("start", start)
                .param("end", end).get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", hasItem(startsWith(recoveryPrefix)))
                .body("name", hasItem(is(waitinMessage)));

        Thread.sleep(2000);
    }

    /**
     * Tests the behaviour when experts sends 'finished' signal
     */
    @Test
    public void finishedConditionCase() throws InterruptedException {


        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 2);
        String end = DatatypeConverter.printDateTime(cal);
        cal.add(Calendar.YEAR, -1);
        String start = DatatypeConverter.printDateTime(cal);

        RecoveryRequest r = generateRecoveryRequest(10L);
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryId", equalTo(1));

        given().queryParam("start", start)
                .param("end", end).get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", hasItem(startsWith(recoveryPrefix)))
                .body("name", hasItem(is(waitinMessage)));


        given().get("/acceptanceDecision/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10),
                        "acceptanceDecision", equalTo("awaiting approval"));

        given().header(jsonHeader).body(10L).post("/finished").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()));


        given().get("/acceptanceDecision/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10),
                        "acceptanceDecision", equalTo("finished"));


        Thread.sleep(2000);
    }

    /**
     * Tests the behaviour when experts sends 'finished' signal after having request accepted
     */
    @Test
    public void finishedAfterManyConditionsCase() throws InterruptedException {


        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 1);
        String end = DatatypeConverter.printDateTime(cal);
        cal.add(Calendar.YEAR, -2);
        String start = DatatypeConverter.printDateTime(cal);


        RecoveryRequest r = generateRecoveryRequest(10L);

        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryId", equalTo(1));


        Thread.sleep(3000);
        given().queryParam("start", start)
                .param("end", end).get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage)));


        /* Approve current request */
        ApprovalResponse approvalResponse = generateApprovalResponse(1L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse);

        Thread.sleep(timeToExecute / 2);

        // TODO: here for some reason waiting message is not present
        given().queryParam("start", start)
                .param("end", end).get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix)));


        // wait until job finishes and observe period starts
        Thread.sleep(timeToExecute / 2 + observePeriod / 2);

        given().queryParam("start", start)
                .param("end", end).get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage)));

        RecoveryRequest r2 = generateRecoveryRequest(11L);
        r2.setSameProblem(true);

        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("acceptedToContinue"),
                        "recoveryId", equalTo(2));

        given().queryParam("start", start)
                .param("end", end).get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage), is(waitinMessage)));


        given().get("/acceptanceDecision/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10, 11),
                        "acceptanceDecision", equalTo("acceptedToContinue"));

        given().header(jsonHeader).body(11L).post("/finished").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()));


        given().get("/acceptanceDecision/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10, 11),
                        "acceptanceDecision", equalTo("finished"));


        Thread.sleep(2000);
    }

    private RecoveryRequest generateRecoveryRequest(Long problemId) {
        RecoveryRequest r = RecoveryRequest.builder().
                problemId(problemId).recoverySteps(new ArrayList<>()).build();
        r.getRecoverySteps().add(RecoveryRequestStep.builder().build());
        return r;
    }

    private ApprovalResponse generateApprovalResponse(Long recoveryRequestId, Integer stepIndex) {
        ApprovalResponse approvalResponse = new ApprovalResponse();
        approvalResponse.setRecoveryId(recoveryRequestId);
        approvalResponse.setStep(stepIndex);
        approvalResponse.setApproved(true);
        return approvalResponse;
    }

    @Test
    public void twoRequests() throws InterruptedException {

        RecoveryRequest r = generateRecoveryRequest(10L);


        /*
         * 1st recovery request - accepted
         */
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryId", equalTo(1));

        /* Check acceptanceDecision of current recovery*/
        given().get("/acceptanceDecision/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10),
                        "acceptanceDecision", equalTo("awaiting approval"));


        /* Check whether recovery records are in database */
        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage)));


        /* Approve current request */
        ApprovalResponse approvalResponse = generateApprovalResponse(1L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse);

        Thread.sleep(timeToExecute / 2);

        /* Check recovery records in database */
        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix)));

        /* Check current acceptanceDecision*/
        given().get("/acceptanceDecision/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10),
                        //TODO:"acceptanceDecision", equalTo("awaiting approval"),
                        "jobStatuses.find { it.stepIndex == 0 }.acceptanceDecision", equalTo("recovering"),
                        "jobStatuses.find { it.stepIndex == 0 }.started", notNullValue(),
                        "jobStatuses.find { it.stepIndex == 0 }.finished", nullValue(),
                        "jobStatuses.find { it.stepIndex == 0 }.timesExecuted", is(0));


        // The second one is not accepted, the other is being processed
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("rejected"),
                        "recoveryId", equalTo(2),
                        "rejectedDueToConditionId", equalTo(10));

//        given().get("/acceptanceDecision/2/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
//                .body("acceptanceDecision", equalTo("rejected"));


        Thread.sleep(timeToExecute / 2 + observePeriod / 2); // end up in the middle of observing period

        given().get("/acceptanceDecision/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10),
                        "jobStatuses.find { it.stepIndex == 0 }.acceptanceDecision", equalTo("finished"),
                        "jobStatuses.find { it.stepIndex == 0 }.started", notNullValue(),
                        "jobStatuses.find { it.stepIndex == 0 }.finished", notNullValue(),
                        "jobStatuses.find { it.stepIndex == 0 }.timesExecuted", is(1));

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage)));

        System.out.println("Sleeping ");
        Thread.sleep(observePeriod); // make sure you pass another half of observing period
        System.out.println("Slept ");


        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage), is(waitinMessage)));
    }

    @Test
    @Ignore
    public void acceptNonExisting() {

        ApprovalResponse approvalResponse = new ApprovalResponse();
        approvalResponse.setRecoveryId(1L);
        approvalResponse.setStep(0);

        stompSession.send(SEND_APPROVE, approvalResponse);
        Assert.fail("Check what's the response");
    }

    @Test
    public void preemptionTest() throws InterruptedException {

        System.out.println("Preemption oneRequestTEset");


        //System.out.println(recoverySequenceController);
        System.out.println(recoveryService);

        //Assert.assertEquals("Nothing before", 0, recoveryService.getOngoingProblems().size());

        RecoveryRequest r = generateRecoveryRequest(10L);

        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryId", equalTo(1));

        given().get("/acceptanceDecision/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10),
                        "acceptanceDecision", equalTo("awaiting approval"));

        //Assert.assertEquals(1, recoveryService.getOngoingProblems().size());

        RecoveryRequest r2 = generateRecoveryRequest(11L);


        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("rejected"),
                        "recoveryId", equalTo(2));

//        given().get("/acceptanceDecision/2/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
//                .body(
//                        "problemId", equalTo(11),
//                        "acceptanceDecision", equalTo("rejected"));

        r2.setWithInterrupt(true);

        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("acceptedWithPreemption"),
                        "recoveryId", equalTo(3)
                );

        given().get("/acceptanceDecision/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(11),
                        "acceptanceDecision", equalTo("awaiting approval"));

        // TODO: support acceptanceDecision by id
//        given().get("/acceptanceDecision/1/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
//                .body(
//                        "conditionIds", contains(10));


        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(is(waitinMessage)), startsWith(recoveryPrefix), is(is(waitinMessage))));

        //Assert.assertEquals(2, recoveryService.getOngoingProblems().size());

        ApprovalResponse approvalResponse = generateApprovalResponse(3L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse);


        Thread.sleep(timeToExecute / 2);


        given().header(jsonHeader).body(11L).post("/finished").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()));

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", hasSize(5))
                .body("name", contains(startsWith("Recovery of"), is(waitinMessage), startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix)))
                .body("end", contains(notNullValue(), notNullValue(), nullValue(), notNullValue(), nullValue()));

        Thread.sleep(timeToExecute / 2 + observePeriod / 2); // after 5 seconds the job will finish


        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", hasSize(6))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage)))
                .body("end", contains(notNullValue(), notNullValue(), nullValue(), notNullValue(), notNullValue(), nullValue()));


        //Assert.assertEquals(1, recoveryService.getOngoingProblems().size());

        System.out.println("+++++++++++");
        Thread.sleep(observePeriod); // after 15 seconds the observingMessage will finish


        System.out.println("===========");
        //Assert.assertEquals(1, recoveryService.getOngoingProblems().size());

        /* 1st recoveyr request will be preempted by 2nd request, after it's finished, 1st will be picked up again as it was not finished */
        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage), startsWith(recoveryPrefix), is(waitinMessage)))
                .body("end", contains(notNullValue(), notNullValue(), notNullValue(), notNullValue(), notNullValue(), notNullValue(), nullValue(), nullValue()));
    }

    /**
     * 1st condition starts the recovery, than during observe period comes 2nd condition of the same type.
     */
    @Test
    public void multipleRequestsResultInOneRecovery() throws InterruptedException {
        RecoveryRequest r = generateRecoveryRequest(10L);

        Assert.assertEquals("No approval requests at this point", 0, approvalRequests.size());
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryId", equalTo(1));

        //Thread.sleep(10000);
        //Assert.assertEquals(1, approvalRequests.size());
        //Assert.assertThat(approvalRequests, contains(hasProperty("recoveryId", is(1L))));
        //approvalRequests.poll();

        ApprovalResponse approvalResponse = generateApprovalResponse(1L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse);

        RecoveryRequest r2 = generateRecoveryRequest(11L);

        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("rejected"),
                        "recoveryId", equalTo(2));


        // wait until job finishes and observe period starts
        Thread.sleep(timeToExecute + observePeriod / 2);

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage)))
                .body("end", contains(nullValue(), notNullValue(), notNullValue(), nullValue()));

        r2.setSameProblem(true);
        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("acceptedToContinue"),
                        "recoveryId", equalTo(3),
                        "continuesTheConditionId", equalTo(10));

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage), is(waitinMessage)))
                .body("end", contains(nullValue(), notNullValue(), notNullValue(), notNullValue(), nullValue()));


        //Assert.assertEquals(1, approvalRequests.size());
        //Assert.assertThat(approvalRequests, contains(hasProperty("recoveryId", is(1L))));

        ApprovalResponse approvalResponse2 = generateApprovalResponse(1L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse2);

        Thread.sleep(observePeriod / 2);

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", hasSize(6))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage), is(waitinMessage), startsWith(executingPrefix)))
                .body("end", contains(nullValue(), notNullValue(), notNullValue(), notNullValue(), notNullValue(), nullValue()));


    }

    /**
     * 1st condition, the important one, finishes after recovery, during observe period comes 2nd condition, the less
     * important one, and it waits until observe period ends
     */
    @Test
    public void lessImportantConditionWaitsUntilObservePeriodEnds() {

    }

    @TestConfiguration
    public static class TestConfig {
        @Bean
        public RcmsController rcmsController() {
            return new RcmsControllerMock();
        }

        public class RcmsControllerMock extends RcmsController {


            @Override
            public void recoverAndWait(RecoveryJob recoveryJob) throws LV0AutomatorControlException {
                System.out.println("Recovery mock job started");
                try {
                    Thread.sleep(timeToExecute);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Recovery mock job finished");
            }

            @Override
            public void sendTTCHardReset() throws LV0AutomatorControlException {
                System.out.println("TTCHardReset mock job started");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("TTCHardReset mock job finished");
            }

            @Override
            public void interrupt() {
                System.out.println("Interrupt mock job started");
            }

        }


    }

    private class DefaultStompFrameHandler implements StompFrameHandler {
        @Override
        public Type getPayloadType(StompHeaders stompHeaders) {
            return ApprovalRequest.class;
        }

        @Override
        public void handleFrame(StompHeaders stompHeaders, Object o) {
            System.out.println("Handle frame: " + o.toString());
            if (o instanceof ApprovalRequest) {
                approvalRequests.add((ApprovalRequest) o);
            } else {
                Assert.fail("Client received unexpected request");
            }
        }
    }

    private String recoveryPrefix = "Recovery of";

    private String executingPrefix = "Executing ";

    private String waitinMessage = "Waiting for approval";

    private String observingMessage = "Observing ..";


}
