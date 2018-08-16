package ch.cern.cms.daq.expertcontroller.rcmsController;

import ch.cern.cms.daq.expertcontroller.RecoveryManager;
import ch.cern.cms.daq.expertcontroller.api.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.api.RecoveryRequestStep;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Component("rcmsController")
public class RcmsController {

    private static Logger logger = Logger.getLogger(RcmsController.class);

    @Autowired
    private RecoveryManager recoveryManager;

    @Value("${rcms.uri}")
    private String AUTOMATOR_URI;

    @Value("${l0.uri}")
    private String L0_URI;

    public String getSubsystemStatus(){
        return null;
    }

    private static final String senderURI = "lv0a-controller.local";


    public RcmsController(){
        logger.info("RCMS controller will use following automator URI: " + AUTOMATOR_URI);
        logger.info("RCMS controller will use following levelzero URI: " + L0_URI);
    }


    public void execute(RecoveryRequest request, RecoveryRequestStep recoveryRequestStep) throws LV0AutomatorControlException {

        // check if this is a ttchr request
        if(isTTCHardResetOnlyRequest(recoveryRequestStep)){
            sendTTCHardReset();
        } else{
            recoverAndWait(request, recoveryRequestStep);
        }
    }

    private boolean isTTCHardResetOnlyRequest(RecoveryRequestStep step){
        if(step.getIssueTTCHardReset() != null && step.getIssueTTCHardReset()){
            if(step.getGreenRecycle().size() != 0){
                return false;
            }
            if(step.getRedRecycle().size() != 0){
                return false;
            }
            if(step.getReset().size() != 0){
                return false;
            }
            return true;
        } else{
            return false;
        }
    }

    // TODO: change to private/protected
    public void sendTTCHardReset() throws  LV0AutomatorControlException{
        logger.info("Issuing TTCHardReset");
        L0Controller controller = new L0Controller(senderURI);
        controller.addURI(L0_URI);
        boolean successfully = controller.sendTTCHardReset();
        logger.info("TTCHardReset executed " + (successfully?"successfully":"unsuccessfully"));
    }

    protected void recoverAndWait(RecoveryRequest request, RecoveryRequestStep recoveryRequestStep) throws LV0AutomatorControlException {

        LV0AutomatorController controller = new LV0AutomatorController(senderURI);

        controller.addURI(AUTOMATOR_URI);

        controller.interruptRecovery();

        Map<String, String> schedules = new HashMap<>();

        for(String subsystem : recoveryRequestStep.getRedRecycle()){
            schedules.put(subsystem, LV0AutomatorController.SUBSYSTEM_SCHEDULE_RECYCLE);
        }

        for(String subsystem: recoveryRequestStep.getGreenRecycle()){
            schedules.put(subsystem, LV0AutomatorController.SUBSYSTEM_SCHEDULE_RECONFIGURE);
        }
        for(String subsystem: recoveryRequestStep.getReset()){
            schedules.put(subsystem, LV0AutomatorController.SUBSYSTEM_SCHEDULE_NONE);
        }

        for(String subsystem: recoveryRequestStep.getFault()){
            controller.setFault(subsystem,true);
        }
        controller.setSchedules(schedules);
        controller.startRecovery();

        while (controller.isRecoveryOngoing()) {
            String recoveryAction = controller.getRecoveryAction();
            Map<String, String> subsytemRecoveryActions = controller.getSubsystemRecoveryActions();

            logger.trace(recoveryAction);
            logger.trace(subsytemRecoveryActions);

            String status = recoveryRequestStep.getStatus();
            if(!status.equalsIgnoreCase(recoveryAction)){
                recoveryRequestStep.setStatus(recoveryAction);
                recoveryManager.handleRecoveryStateUpdate(request);
            }

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

    public void interrupt(){

        LV0AutomatorController controller = null;
        try {
            controller = new LV0AutomatorController("lv0a-controller.local");
            controller.addURI(AUTOMATOR_URI);
            controller.interruptRecovery();
        } catch (LV0AutomatorControlException e){
            e.printStackTrace();
        }
    }


}
