package ch.cern.cms.daq.expertcontroller.service;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LoopBreaker {

    /**
     * Number of executions in given time window
     */
    @Getter
    @Value("${recoverylimit.count}")
    protected Integer numberOfExecutions;

    /**
     * Time window in seconds
     */
    @Getter
    @Value("${recoverylimit.timewindow.seconds}")
    protected Integer timeWindow;

    Set<OffsetDateTime> currentExecutions = new HashSet<>();

    private static Logger logger = LoggerFactory.getLogger(LoopBreaker.class);


    public boolean exceedsTheLimit(){

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = now.minusSeconds(timeWindow);


        // 1. update the entries based on time
        currentExecutions = currentExecutions.stream()
                .filter(e->e.isAfter(threshold))
                .collect(Collectors.toSet());

        // 2. if we did reject
        if(numberOfExecutions <= currentExecutions.size()){
            logger.info("Reached the limit of executions in time.");
            return true;
        }

        // 3. if we didn't add
        else{
            logger.info("Limit not reached: " + currentExecutions);
            return false;
        }

    }

    public void registerAccepted(){
        currentExecutions.add(OffsetDateTime.now());
    }
}
