package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalRequest;
import ch.cern.cms.daq.expertcontroller.datatransfer.ApprovalResponse;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRequestStep;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.repository.RecoveryProcedureRepository;
import ch.cern.cms.daq.expertcontroller.service.IRecoveryService;
import ch.cern.cms.daq.expertcontroller.service.rcms.LV0AutomatorControlException;
import ch.cern.cms.daq.expertcontroller.service.rcms.RcmsController;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.IExecutor;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.TestExecutorFactory;
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

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.post;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;


//TODO: add oneRequestTEset when condition finishes itself, add oneRequestTEset when interrupt from LV0
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
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
    private IRecoveryService recoveryService;

    @Autowired
    private RecoveryProcedureRepository recoveryProcedureRepository;


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


        Assert.assertEquals(0, recoveryProcedureRepository.findAll().size());

        given().header(jsonHeader).get("/interrupt").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()));

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
    public void checkRecoveryRecordsAfterOneRequestTest() {


        // 1. create recovery request with one step
        RecoveryRequest r = RecoveryRequest.builder()
                .problemId(101L)
                .recoverySteps(Arrays.asList(
                        RecoveryRequestStep.builder()
                                .humanReadable("J1")
                                .build()))
                .build();

        // 2. send to the controller & assert accepted
        given().header(jsonHeader).body(r)
                .post("/recover").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryProcedureId", notNullValue()
                );


        // 3. Assert awaitingApproval status of the service
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("AwaitingApproval")
                );

        //4. Assert status of the procedure
        // TODO: if have procedure id


        OffsetDateTime start = OffsetDateTime.now().minusYears(1);
        OffsetDateTime end = OffsetDateTime.now().plusYears(1);
        System.out.println("All: " + recoveryProcedureRepository.findAll());

        // 5. Assert correct recovery records
        given().queryParam("start", start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .param("end", end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", hasItem(startsWith("Recovery procedure #")));
        //TODO: expect job results

    }

    /**
     * Tests the behaviour when experts sends 'finished' signal before executor even starts the recovery procedure
     */
    @Test
    public void finishedSignalOnAwaitingApprovalTest() {

        // 1. build request
        RecoveryRequest r = RecoveryRequest.builder()
                .problemId(101L)
                .recoverySteps(Arrays.asList(
                        RecoveryRequestStep.builder()
                                .humanReadable("J1")
                                .build()))
                .build();

        // 2. send request, assert that it's accepted
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryProcedureId", notNullValue());


        // 3. Assert status of the service
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("AwaitingApproval")
                );

        // 4. Send finish signal
        given().header(jsonHeader).body(101L).post("/finished").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()));


        // 5. Assert status of the service
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("Idle"),
                        "lastProcedureStatus.actionSummary", equalTo(Arrays.asList("Recovery procedure has been cancelled")),
                        "lastProcedureStatus.finalStatus", equalTo("Cancelled")
                );

        // TODO: assert remaining status fields: id=null, jobStatuses=null, conditionIds=null,

    }

    /**
     * Tests the behaviour when experts sends 'finished' signal on observing time
     */
    @Test
    public void finishedSignalOnObservingTest() throws InterruptedException {

        // 1. build request
        RecoveryRequest r = RecoveryRequest.builder()
                .problemId(101L)
                .recoverySteps(Arrays.asList(
                        RecoveryRequestStep.builder()
                                .humanReadable("J1")
                                .build()))
                .build();

        // 2. send request, assert that it's accepted
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryProcedureId", notNullValue());


        // 3. Assert status of the service
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("AwaitingApproval")
                );


        ApprovalResponse approvalResponse = generateApprovalResponse(10L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse);

        // 4. Send finish signal
        given().header(jsonHeader).body(101L).post("/finished").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()));

        Thread.sleep(2000);


        // 5. Assert status of the service
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("Idle"),
                        "lastProcedureStatus.actionSummary", equalTo(Arrays.asList("Job J1 accepted", "Job J1 completed", "Recovery procedure completed successfully")),
                        "lastProcedureStatus.finalStatus", equalTo("Completed")
                );

        // TODO: assert remaining status fields: id=null, jobStatuses=null, conditionIds=null,

    }


    /**
     * Tests the behaviour for 2-job recovery procedure where 1st job doesn't fix the problem and 2nd does
     */
    @Test
    public void twoStepRecoveryProcedureWhereSecondStepFixesTheProblemTest() throws InterruptedException {

        // 1. build request
        RecoveryRequest r = RecoveryRequest.builder()
                .problemId(101L)
                .problemTitle("Problem T1")
                .recoverySteps(Arrays.asList(
                        RecoveryRequestStep.builder()
                                .humanReadable("J1")
                                .stepIndex(0)
                                .build(),

                        RecoveryRequestStep.builder()
                                .humanReadable("J2")
                                .stepIndex(1)
                                .build()))
                .build();

        // 2. send request, assert that it's accepted
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryProcedureId", notNullValue());


        ApprovalResponse approvalResponse = generateApprovalResponse(10L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse);


        // 3. wait half time of the execution
        Thread.sleep(TestExecutorFactory.recoveringTime / 2);

        // 4. Assert status of the service while it's still executing
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("Recovering")
                );


        // 5. wait until recovering finishes and observe period starts
        Thread.sleep(TestExecutorFactory.recoveringTime / 2 + TestExecutorFactory.observingTime / 2);

        // 6. build 2nd request
        RecoveryRequest r2 = RecoveryRequest.builder()
                .problemId(101L)
                .isSameProblem(true)
                .recoverySteps(Arrays.asList(
                        RecoveryRequestStep.builder()
                                .humanReadable("J1")
                                .build()))
                .build();


        //7. send and assert status: accepted to continue
        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("acceptedToContinue"),
                        "recoveryProcedureId", notNullValue());

        // 8. Assert status of the service, job should be finished by now, should be in observing
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("Observe")
                );

        Thread.sleep(TestExecutorFactory.observingTime / 2);


        // 9. Assert status of the service, 1st job and observation period should be finished by now
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("AwaitingApproval")
                );

        ApprovalResponse approvalResponse2 = generateApprovalResponse(10L, 1);
        stompSession.send(SEND_APPROVE, approvalResponse2);


        given().header(jsonHeader).body(101L).post("/finished").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()));


        // Finished signal was sent during recovering state - it will be delayed until observe period
        Thread.sleep(TestExecutorFactory.recoveringTime);


        // 8. Assert status of the service after 2nd job
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("Idle")
                );


    }

    private RecoveryRequest generateRecoveryRequest(Long problemId) {
        RecoveryRequest r = RecoveryRequest.builder().
                problemId(problemId).recoverySteps(new ArrayList<>()).build();
        r.getRecoverySteps().add(RecoveryRequestStep.builder().build());
        return r;
    }

    private ApprovalResponse generateApprovalResponse(Long recoveryRequestId, Integer stepIndex) {
        ApprovalResponse approvalResponse = new ApprovalResponse();
        approvalResponse.setRecoveryProcedureId(recoveryRequestId);
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
                        "recoveryProcedureId", equalTo(1));

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
                        "recoveryProcedureId", equalTo(2),
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
        approvalResponse.setRecoveryProcedureId(1L);
        approvalResponse.setStep(0);

        stompSession.send(SEND_APPROVE, approvalResponse);
        Assert.fail("Check what's the response");
    }

    @Test
    public void rejectionTest() {
        // 1. build request
        RecoveryRequest r = RecoveryRequest.builder()
                .problemId(101L)
                .problemTitle("Problem T1")
                .recoverySteps(Arrays.asList(
                        RecoveryRequestStep.builder()
                                .humanReadable("J1")
                                .stepIndex(0)
                                .build()))
                .build();

        // 2. send request, assert that it's accepted
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryProcedureId", notNullValue());

        //3. check status of the service, should be awaiting approval
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("AwaitingApproval"),
                        "lastProcedureStatus.id", equalTo(10)
                );


        // 4. build 2nd request
        RecoveryRequest r2 = RecoveryRequest.builder()
                .problemId(101L)
                .problemTitle("Problem T1")
                .recoverySteps(Arrays.asList(
                        RecoveryRequestStep.builder()
                                .humanReadable("J1")
                                .stepIndex(0)
                                .build()))
                .build();

        // 5. send request, assert that it's accepted
        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("rejected"),
                        "recoveryProcedureId", nullValue(),
                        "rejectedDueToConditionId", equalTo(101)
                );
    }

    /**
     * Test the preemption behaviour
     */
    @Test
    public void preemptionTest() {

        // 1. build request
        RecoveryRequest r = RecoveryRequest.builder()
                .problemId(101L)
                .problemTitle("Problem T1")
                .recoverySteps(Arrays.asList(
                        RecoveryRequestStep.builder()
                                .humanReadable("J1")
                                .stepIndex(0)
                                .build()))
                .build();

        // 2. send request, assert that it's accepted
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("accepted"),
                        "recoveryProcedureId", notNullValue());

        //3. check status of the service, should be awaiting approval
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("AwaitingApproval"),
                        "lastProcedureStatus.id", equalTo(10)
                );


        //4. create preempting request
        r.setWithInterrupt(true);

        //5. send 2nd request, assert that has been accepted with preemption
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("acceptedWithPreemption"),
                        "recoveryProcedureId", notNullValue());

        //3. check status of the service, should be awaiting approval
        given().header(jsonHeader)
                .get("/service-status").then()
                .assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "executorState", equalTo("AwaitingApproval"),
                        "lastProcedureStatus.id", equalTo(11) // NOTE that new procedure has been created
                );
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
                        "recoveryProcedureId", equalTo(1));

        //Thread.sleep(10000);
        //Assert.assertEquals(1, approvalRequests.size());
        //Assert.assertThat(approvalRequests, contains(hasProperty("recoveryProcedureId", is(1L))));
        //approvalRequests.poll();

        ApprovalResponse approvalResponse = generateApprovalResponse(1L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse);

        RecoveryRequest r2 = generateRecoveryRequest(11L);

        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "acceptanceDecision", equalTo("rejected"),
                        "recoveryProcedureId", equalTo(2));


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
                        "recoveryProcedureId", equalTo(3),
                        "continuesTheConditionId", equalTo(10));

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage), is(waitinMessage)))
                .body("end", contains(nullValue(), notNullValue(), notNullValue(), notNullValue(), nullValue()));


        //Assert.assertEquals(1, approvalRequests.size());
        //Assert.assertThat(approvalRequests, contains(hasProperty("recoveryProcedureId", is(1L))));

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
        public IExecutor executorService() {
            return TestExecutorFactory.INTEGRATION_TEST_EXECUTOR;
        }

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

    @Deprecated
    private String recoveryPrefix = "Recovery of";

    @Deprecated
    private String executingPrefix = "Executing ";

    @Deprecated
    private String waitinMessage = "Waiting for approval";

    @Deprecated
    private String observingMessage = "Observing ..";


}
