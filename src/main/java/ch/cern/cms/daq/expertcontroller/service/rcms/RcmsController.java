package ch.cern.cms.daq.expertcontroller.service.rcms;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import rcms.fm.fw.service.command.CommandServiceException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;


@Service
public class RcmsController {

    private static Logger logger = Logger.getLogger(RcmsController.class);

    @Value("${rcms.uri}")
    private String AUTOMATOR_URI;

    @Value("${l0.uri}")
    private String L0_URI;

    @Value("${rcms.uri}")
    private String senderURI;

    public String getSubsystemStatus() {
        return null;
    }

    @Setter
    private Consumer<String> rcmsStatusConsumer;


    public RcmsController() {
        logger.info("RCMS controller will use following automator URI: " + AUTOMATOR_URI);
        logger.info("RCMS controller will use following levelzero URI: " + L0_URI);
    }


    public void execute(RecoveryJob recoveryJob) throws LV0AutomatorControlException, CommandServiceException {

        logger.info("Recovery job submitted to RCMS controller: " + recoveryJob.toCompactString());
        // check if this is a ttchr request
        if (isTTCHardResetOnlyRequest(recoveryJob)) {
            logger.debug("Request generates TTC Hard Reset");
            sendTTCHardReset();
        }
        else {
            logger.debug("Request generates recover & wait");
            recoverAndWait(recoveryJob);
        }

    }

    private boolean isTTCHardResetOnlyRequest(RecoveryJob recoveryJob) {
        if (recoveryJob.getIssueTTCHardReset() != null && recoveryJob.getIssueTTCHardReset()) {
            if (recoveryJob.getGreenRecycle().size() != 0) {
                return false;
            }
            if (recoveryJob.getRedRecycle().size() != 0) {
                return false;
            }
            if (recoveryJob.getReset().size() != 0) {
                return false;
            }
            return true;
        } else {
            return false;
        }
    }

    private boolean isLongRecovery(RecoveryJob recoveryJob) {
        if (recoveryJob.getGreenRecycle() != null && recoveryJob.getGreenRecycle().size() > 0) {
            return true;
        }
        if (recoveryJob.getRedRecycle() != null && recoveryJob.getRedRecycle().size() > 0) {
            return true;
        }
        if (recoveryJob.getReset() != null && recoveryJob.getReset().size() > 0) {
            return true;
        }
        return false;
    }

    // TODO: change to private/protected
    public void sendTTCHardReset() throws CommandServiceException, LV0AutomatorControlException {
        logger.info("Issuing TTCHardReset");
        L0Controller controller = new L0Controller(senderURI);
        controller.addURI(L0_URI);
        controller.sendTTCHardReset();
        logger.info("TTCHardReset executed");
    }

    protected void recoverAndWait(RecoveryJob recoveryJob) throws LV0AutomatorControlException {

        LV0AutomatorController controller = new LV0AutomatorController(senderURI);

        controller.addURI(AUTOMATOR_URI);

        controller.interruptRecovery();

        logger.debug("Setting the schedule for " + recoveryJob.toCompactString());

        Map<String, String> schedules = new HashMap<>();

        for (String subsystem : recoveryJob.getRedRecycle()) {
            logger.debug("Setting red-recycle for: " + subsystem);
            schedules.put(subsystem, LV0AutomatorController.SUBSYSTEM_SCHEDULE_RECYCLE);
        }

        for (String subsystem : recoveryJob.getGreenRecycle()) {
            logger.debug("Setting green-recycle for: " + subsystem);
            schedules.put(subsystem, LV0AutomatorController.SUBSYSTEM_SCHEDULE_RECONFIGURE);
        }
        for (String subsystem : recoveryJob.getReset()) {
            logger.debug("Clearing for: " + subsystem);
            schedules.put(subsystem, LV0AutomatorController.SUBSYSTEM_SCHEDULE_NONE);
        }

        for (String subsystem : recoveryJob.getFault()) {
            logger.debug("Setting at-fault for: " + subsystem);
            controller.setFault(subsystem, true);
        }


        controller.setSchedules(schedules);
        controller.startRecovery();

        String lastRecoveryAction = null;

        while (controller.isRecoveryOngoing()) {
            String recoveryAction = controller.getRecoveryAction();
            Map<String, String> subsytemRecoveryActions = controller.getSubsystemRecoveryActions();

            logger.trace(recoveryAction);
            logger.trace(subsytemRecoveryActions);

            if(lastRecoveryAction != recoveryAction){
                if (rcmsStatusConsumer != null) {
                    logger.info("New L0A status: " + recoveryAction);
                    rcmsStatusConsumer.accept(recoveryAction);
                }
            }
            lastRecoveryAction = recoveryAction;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException intEx) {
                intEx.printStackTrace();
                break;
            }
        }
    }

    public Boolean isRecoveryOngoing() {

        LV0AutomatorController controller = null;
        try {
            controller = new LV0AutomatorController("lv0a-controller.local");
            controller.addURI(AUTOMATOR_URI);
            return controller.isRecoveryOngoing();
        } catch (LV0AutomatorControlException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void interrupt() {

        LV0AutomatorController controller = null;
        try {
            controller = new LV0AutomatorController("lv0a-controller.local");
            controller.addURI(AUTOMATOR_URI);
            controller.interruptRecovery();
        } catch (LV0AutomatorControlException e) {
            e.printStackTrace();
        }
    }


}
