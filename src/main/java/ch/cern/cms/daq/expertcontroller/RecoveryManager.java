package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.rcmsController.LV0AutomatorControlException;
import ch.cern.cms.daq.expertcontroller.websocket.GreetingController;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component("recoveryManager")
public class RecoveryManager {

    @Autowired
    RecoveryController recoveryController;


    @Autowired
    GreetingController greetingController;

    @Autowired
    RecoveryJobRepository recoveryJobRepository;

    HashMap<Long, RecoveryRequest> awaitingApproval = new HashMap<>();

    Logger logger = Logger.getLogger(RecoveryManager.class);

    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);


    /**
     * Will do everything from beginning to end
     */
    public Long recover(RecoveryRequest request){

        logger.info("This is what is gonna happen: " + request);

        request.setStatus("awaiting approval");
        recoveryJobRepository.save(request);
        greetingController.requestApprove(request);
        awaitingApproval.put(request.getId(), request);
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                timeout(request.getId());
            }
        }, 5, TimeUnit.SECONDS);

        return request.getId();
    }

    public String getStatus(Long id){
        Optional<RecoveryRequest> recovery = recoveryJobRepository.findById(id);
        if (recovery.isPresent()){
            String status = recovery.get().getStatus();
            logger.info("The recovery request with id " + id + " has status " + status);
            return status;

        } else{
            return "unknown";
        }

    }

    public void timeout(long id){
        if( awaitingApproval.containsKey(id)){
            RecoveryRequest request = awaitingApproval.get(id);
            awaitingApproval.remove(id);
            request.setStatus("timeout");
            recoveryJobRepository.save(request);

            greetingController.notifyTimeout(id);

        } else{
            logger.warn("Could not found recovery to timeout with given id " + id);
        }
    }

    public void rejected(long id){
        if( awaitingApproval.containsKey(id)){
            RecoveryRequest request = awaitingApproval.get(id);
            awaitingApproval.remove(id);
            request.setStatus("rejected");
            recoveryJobRepository.save(request);

        } else{
            logger.warn("Could not found recovery to reject with given id " + id);
        }
    }

    public void approved(long id){
        // ask shifter for confirmation - this can be timed out by expert

        logger.info("Available requests awaiting approval: " + awaitingApproval);

        if( awaitingApproval.containsKey(id)){

            logger.info("Request " + id + " will be executed");
            RecoveryRequest request = awaitingApproval.get(id);
            awaitingApproval.remove(id);
            request.setStatus("approved");
            recoveryJobRepository.save(request);


            try {
                recoveryController.recoverAndWait(request);
                request.setStatus("finished");
                recoveryJobRepository.save(request);
            } catch (LV0AutomatorControlException e) {

                request.setStatus("failed");
                recoveryJobRepository.save(request);
                e.printStackTrace();
            }

            // report to shifter what happened and say what to do next
            logger.info("This is what happened. ");
        } else {
            logger.warn("Could not found recovery to approve with given id " + id);
        }

    }

}
