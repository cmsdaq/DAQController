package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.api.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.api.RecoveryRequestStep;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecordRepository;
import ch.cern.cms.daq.expertcontroller.rcmsController.RcmsController;
import ch.cern.cms.daq.expertcontroller.websocket.ApprovalResponse;
import ch.cern.cms.daq.expertcontroller.websocket.DashboardController;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * This is to configure the application for the usage in webapplication servlet - tomcat.
 */
@SpringBootApplication
public class ExpertControllerServletApplication extends SpringBootServletInitializer {

    @Autowired
    ExpertController expertController;

    @Autowired
    DashboardController dashboardController;

    private static Logger logger = Logger.getLogger(ExpertControllerServletApplication.class);

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(ExpertControllerServletApplication.class).properties("spring.config.name: controller");
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(ExpertControllerServletApplication.class, args);
    }

    @Bean
    CommandLineRunner init(RcmsController controller) {
        return (evt) -> {


            System.out.println("Sending ttchard reset");
            controller.sendTTCHardReset();
            System.out.println("Sending ttchard reset");


        };
    }

    CommandLineRunner init(RecoveryRecordRepository repo) {
        return (evt) -> {

            RecoveryRequest recoveryRequest = new RecoveryRequest();

            recoveryRequest.setProblemId(997L);
            recoveryRequest.setRecoverySteps(new ArrayList<>());

            RecoveryRequestStep step1 = new RecoveryRequestStep();
            step1.setGreenRecycle(new HashSet<>());
            step1.getGreenRecycle().add("ECAL");
            recoveryRequest.getRecoverySteps().add(step1);

            logger.info("Sending test request");
            System.out.println("Sending test request");
            expertController.greeting(recoveryRequest);

            Thread.sleep(10000);

            ApprovalResponse ar = new ApprovalResponse();
            ar.setRecoveryId(997L);
            ar.setApproved(true);
            ar.setStep(0);

            logger.info("Sending test approval");
            System.out.println("Sending test approval");
            dashboardController.approve(ar);

        };
    }

}