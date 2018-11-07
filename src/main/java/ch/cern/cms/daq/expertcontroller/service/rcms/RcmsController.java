package ch.cern.cms.daq.expertcontroller.service.rcms;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Component("rcms")
public class RcmsController {

    private static Logger logger = Logger.getLogger(RcmsController.class);


    @Value("${rcms.uri}")
    private String AUTOMATOR_URI;

    @Value("${l0.uri}")
    private String L0_URI;

    public String getSubsystemStatus() {
        return null;
    }

    @Value("${rcms.uri}")
    private String senderURI;


    public RcmsController() {
        logger.info("RCMS controller will use following automator URI: " + AUTOMATOR_URI);
        logger.info("RCMS controller will use following levelzero URI: " + L0_URI);
    }


    public void execute(RecoveryJob recoveryJob) throws LV0AutomatorControlException {

        // check if this is a ttchr request
        if (isTTCHardResetOnlyRequest(recoveryJob)) {
            sendTTCHardReset();
        } else {
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

    // TODO: change to private/protected
    public void sendTTCHardReset() throws LV0AutomatorControlException {
        logger.info("Issuing TTCHardReset");
        L0Controller controller = new L0Controller(senderURI);
        controller.addURI(L0_URI);
        boolean successfully = controller.sendTTCHardReset();
        logger.info("TTCHardReset executed " + (successfully ? "successfully" : "unsuccessfully"));
    }

    protected void recoverAndWait(RecoveryJob recoveryJob) throws LV0AutomatorControlException {

        LV0AutomatorController controller = new LV0AutomatorController(senderURI);

        controller.addURI(AUTOMATOR_URI);

        controller.interruptRecovery();

        logger.debug("Setting the schedule for " + recoveryJob);

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

        while (controller.isRecoveryOngoing()) {
            String recoveryAction = controller.getRecoveryAction();
            Map<String, String> subsytemRecoveryActions = controller.getSubsystemRecoveryActions();

            logger.trace(recoveryAction);
            logger.trace(subsytemRecoveryActions);

            //TODO: do sth whenever finalStatus changes. Perhaps callback will be ok.

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
