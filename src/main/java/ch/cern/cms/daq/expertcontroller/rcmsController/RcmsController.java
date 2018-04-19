package ch.cern.cms.daq.expertcontroller.rcmsController;

import ch.cern.cms.daq.expertcontroller.RecoveryManager;
import ch.cern.cms.daq.expertcontroller.api.RecoveryRequest;
import ch.cern.cms.daq.expertcontroller.api.RecoveryRequestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Component("rcmsController")
public class RcmsController {


    @Autowired
    private RecoveryManager recoveryManager;

    // TODO:
    private static final String AUTOMATOR_URI = "http://dvbu-pcintelsz.cern.ch:18080/urn:rcms-fm:fullpath=/philipp/pccms87/rcms42_port8080/lvl0FMwithAutomator,group=lvl0A,owner=philipp";

    public String getSubsystemStatus(){
        return null;
    }

    public RcmsController(){

    }

    public void recoverAndWait(RecoveryRequest request, RecoveryRequestStep recoveryRequestStep) throws LV0AutomatorControlException {

        LV0AutomatorController controller = new LV0AutomatorController("lv0a-controller.local");

        controller.addAutomatorURI(AUTOMATOR_URI);

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

            System.out.println(recoveryAction);
            System.out.println(subsytemRecoveryActions);

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
            controller.addAutomatorURI(AUTOMATOR_URI);
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
            controller.addAutomatorURI(AUTOMATOR_URI);
            controller.interruptRecovery();
        } catch (LV0AutomatorControlException e){
            e.printStackTrace();
        }
    }


}