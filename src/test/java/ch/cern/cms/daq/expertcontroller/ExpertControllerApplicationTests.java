package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.api.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.api.RecoveryRequestStep;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecordRepository;
import ch.cern.cms.daq.expertcontroller.rcmsController.LV0AutomatorControlException;
import ch.cern.cms.daq.expertcontroller.rcmsController.RcmsController;
import ch.cern.cms.daq.expertcontroller.websocket.ApprovalRequest;
import ch.cern.cms.daq.expertcontroller.websocket.ApprovalResponse;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;


//TODO: add test when condition finishes itself, add test when interrupt from LV0
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ExpertControllerApplicationTests {

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
    private RecoveryRecordRepository recoveryRecordRepository;


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

        System.out.println("All: " + recoveryRecordRepository.findAll());

        System.out.println("Requesting: " + start + "-" + end);
        RecoveryRequest r = new RecoveryRequest();
        r.setProblemId(10L);
        r.setRecoverySteps(new ArrayList<>());
        r.getRecoverySteps().add(new RecoveryRequestStep());
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "status", equalTo("accepted"),
                        "recoveryId", equalTo(1));

        given().queryParam("start", start)
                .param("end", end).get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", hasItem(startsWith(recoveryPrefix)))
                .body("name", hasItem(is(waitinMessage)));

        Thread.sleep(2000);
    }

    private RecoveryRequest generateRecoveryRequest(Long problemId) {
        RecoveryRequest r = new RecoveryRequest();
        r.setProblemId(problemId);
        r.setRecoverySteps(new ArrayList<>());
        r.getRecoverySteps().add(new RecoveryRequestStep());
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
                        "status", equalTo("accepted"),
                        "recoveryId", equalTo(1));

        /* Check status of current recovery*/
        given().get("/status/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10),
                        "status", equalTo("awaiting approval"));


        /* Check whether recovery records are in database */
        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage)));


        /* Approve current request */
        ApprovalResponse approvalResponse = generateApprovalResponse(1L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse);

        Thread.sleep(1000);

        /* Check recovery records in database */
        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix)));

        /* Check current status*/
        given().get("/status/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10),
                        //TODO:"status", equalTo("awaiting approval"),
                        "automatedSteps.find { it.stepIndex == 0 }.status", equalTo("recovering"),
                        "automatedSteps.find { it.stepIndex == 0 }.started", notNullValue(),
                        "automatedSteps.find { it.stepIndex == 0 }.finished", nullValue(),
                        "automatedSteps.find { it.stepIndex == 0 }.timesExecuted", is(0));


        // The second one is not accepted, the other is being processed
        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "status", equalTo("rejected"),
                        "recoveryId", equalTo(2),
                        "rejectedDueToConditionId", equalTo(10));

//        given().get("/status/2/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
//                .body("status", equalTo("rejected"));


        Thread.sleep(10000);

        given().get("/status/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10),
                        "automatedSteps.find { it.stepIndex == 0 }.status", equalTo("finished"),
                        "automatedSteps.find { it.stepIndex == 0 }.started", notNullValue(),
                        "automatedSteps.find { it.stepIndex == 0 }.finished", notNullValue(),
                        "automatedSteps.find { it.stepIndex == 0 }.timesExecuted", is(1));

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage)));
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

        RecoveryRequest r = generateRecoveryRequest(10L);

        given().header(jsonHeader).body(r).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "status", equalTo("accepted"),
                        "recoveryId", equalTo(1));

        given().get("/status/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(10),
                        "status", equalTo("awaiting approval"));


        RecoveryRequest r2 = generateRecoveryRequest(11L);

        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "status", equalTo("rejected"),
                        "recoveryId", equalTo(2));

//        given().get("/status/2/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
//                .body(
//                        "problemId", equalTo(11),
//                        "status", equalTo("rejected"));

        r2.setWithInterrupt(true);

        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "status", equalTo("acceptedWithPreemption"),
                        "recoveryId", equalTo(3)
                );

        given().get("/status/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
                .body(
                        "conditionIds", contains(11),
                        "status", equalTo("awaiting approval"));

        // TODO: support status by id
//        given().get("/status/1/").then().assertThat().statusCode(equalTo(HttpStatus.OK.value()))
//                .body(
//                        "conditionIds", contains(10));


        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(is(waitinMessage)), startsWith(recoveryPrefix), is(is(waitinMessage))));

        ApprovalResponse approvalResponse = generateApprovalResponse(3L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse);


        Thread.sleep(1000);

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith("Recovery of"), is(waitinMessage), startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix)))
                .body("end", contains(notNullValue(), notNullValue(), nullValue(), notNullValue(), nullValue()));

        Thread.sleep(5000); // after 5 seconds the job will finish

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage)))
                .body("end", contains(notNullValue(), notNullValue(), nullValue(), notNullValue(), notNullValue(), nullValue()));

        Thread.sleep(RecoverySequenceController.observePeriod); // after 15 seconds the observingMessage will finish

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
                        "status", equalTo("accepted"),
                        "recoveryId", equalTo(1));

        Thread.sleep(1000);

        Assert.assertEquals(1, approvalRequests.size());
        Assert.assertThat(approvalRequests, contains(hasProperty("recoveryId", is(1L))));
        approvalRequests.poll();

        ApprovalResponse approvalResponse = generateApprovalResponse(1L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse);

        RecoveryRequest r2 = generateRecoveryRequest(11L);

        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "status", equalTo("rejected"),
                        "recoveryId", equalTo(2));


        // wait until job finishes and observe period starts
        Thread.sleep(10000);

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage)))
                .body("end", contains(nullValue(), notNullValue(), notNullValue(), nullValue()));

        r2.setSameProblem(true);
        given().header(jsonHeader).body(r2).post("/recover").then().assertThat()
                .statusCode(equalTo(HttpStatus.CREATED.value()))
                .body(
                        "status", equalTo("acceptedToContinue"),
                        "recoveryId", equalTo(3),
                        "continuesTheConditionId", equalTo(10));

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
                .body("name", contains(startsWith(recoveryPrefix), is(waitinMessage), startsWith(executingPrefix), is(observingMessage), is(waitinMessage)))
                .body("end", contains(nullValue(), notNullValue(), notNullValue(), notNullValue(), nullValue()));


        Assert.assertEquals(1, approvalRequests.size());
        Assert.assertThat(approvalRequests, contains(hasProperty("recoveryId", is(1L))));

        ApprovalResponse approvalResponse2 = generateApprovalResponse(1L, 0);
        stompSession.send(SEND_APPROVE, approvalResponse2);

        given().queryParam("start", "2000-01-01T00:00:00Z")
                .param("end", "3000-01-01T00:00:00Z").get("/records").then().assertThat()
                .statusCode(equalTo(HttpStatus.OK.value()))
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
            public void recoverAndWait(RecoveryRequest request, RecoveryRequestStep recoveryRequestStep) throws LV0AutomatorControlException {
                System.out.println("Recovery mock job started");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Recovery mock job finished");
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
