package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.persistence.RecoveryJobRepository;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecord;
import ch.cern.cms.daq.expertcontroller.persistence.RecoveryRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Calendar;
import java.util.Date;

/**
 * This is an application configuration to run as a standalone mode (packaging jar)
 */
//@SpringBootApplication
public class ExpertControllerStandaloneApplication {

	public static void main2(String[] args) {
		SpringApplication.run(ExpertControllerStandaloneApplication.class, args);


	}

	//@Bean
	CommandLineRunner init(RecoveryRecordRepository repo) {
		return (evt) -> {

			repo.deleteAll();

			Calendar cal = Calendar.getInstance();
// remove next line if you're always using the current time.
			cal.setTime(new Date());
			cal.add(Calendar.HOUR, -1);
			Date oneHourBack = cal.getTime();

			RecoveryRecord rr = new RecoveryRecord();
			rr.setName("a");
			rr.setStart(oneHourBack);
			rr.setEnd(new Date());
			repo.save(rr);
		};
	}


}
