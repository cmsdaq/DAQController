package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.rcmsController.RcmsController;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

/**
 * This is to configure the application for the usage in webapplication servlet - tomcat.
 */
@SpringBootApplication
public class ExpertControllerServletApplication extends SpringBootServletInitializer {

    @Autowired
    ProbeRecoverySender probeRecoverySender;


    @Value("${test.commands.enabled}")
    Boolean runTestCommands;

    private static Logger logger = Logger.getLogger(ExpertControllerServletApplication.class);

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(ExpertControllerServletApplication.class).properties("spring.config.name: controller");
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(ExpertControllerServletApplication.class, args);
    }

    @Bean
    CommandLineRunner init() {
        return (evt) -> {

            if(runTestCommands!= null && runTestCommands) {
                logger.info("Test commands enabled");
                probeRecoverySender.issueTestRecoverySequence(null);

            } else{
                logger.info("Test commands disabled");
            }
        };
    }





}