package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.service.ProbeRecoverySender;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.ExecutorFactory;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.IExecutor;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This is to configure the application for the usage in webapplication servlet - tomcat.
 */
@SpringBootApplication
public class ExpertControllerServletApplication extends SpringBootServletInitializer {

    @Autowired
    ProbeRecoverySender probeRecoverySender;

    private static Logger logger = Logger.getLogger(ExpertControllerServletApplication.class);

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(ExpertControllerServletApplication.class).properties("spring.config.name: controller");
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(ExpertControllerServletApplication.class, args);
    }


    @Configuration
    public static class AppConfig {

        @Bean
        public IExecutor executorService() {
            return ExecutorFactory.DEFAULT_EXECUTOR;
        }
    }

}