package ch.cern.cms.daq.expertcontroller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class ExpertControllerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpertControllerApplication.class, args);
	}
}
