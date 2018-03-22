package ch.cern.cms.daq.expertcontroller;

import ch.cern.cms.daq.expertcontroller.rcmsController.LV0AutomatorControlException;
import ch.cern.cms.daq.expertcontroller.rcmsController.LV0AutomatorController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;


@Component("recoveryController")
public class RecoveryController {


    @Autowired
    private RecoveryJobRepository recoveryJobRepository;
    // TODO:
    private static final String AUTOMATOR_URI = "http://dvbu-pcintelsz.cern.ch:18080/urn:rcms-fm:fullpath=/philipp/pccms87/rcms42_port8080/lvl0FMwithAutomator,group=lvl0A,owner=philipp";

    public String getSubsystemStatus(){
        return null;
    }

    public void recoverAndWait(RecoveryRequest recoveryRequest) throws LV0AutomatorControlException {

        LV0AutomatorController controller = new LV0AutomatorController("lv0a-controller.local");

        controller.addAutomatorURI(AUTOMATOR_URI);

        controller.interruptRecovery();

        Map<String, String> schedules = new HashMap<>();

        for(String subsystem : recoveryRequest.getRedRecycle()){
            schedules.put(subsystem, LV0AutomatorController.SUBSYSTEM_SCHEDULE_RECYCLE);
        }

        for(String subsystem: recoveryRequest.getGreenRecycle()){
            schedules.put(subsystem, LV0AutomatorController.SUBSYSTEM_SCHEDULE_RECONFIGURE);
        }
        for(String subsystem: recoveryRequest.getReset()){
            schedules.put(subsystem, LV0AutomatorController.SUBSYSTEM_SCHEDULE_NONE);
        }

        for(String subsystem: recoveryRequest.getFault()){
            controller.setFault(subsystem,true);
        }
        controller.setSchedules(schedules);


        recoveryRequest.setStarted(new Date());

        controller.startRecovery();
        recoveryJobRepository.save(recoveryRequest);

        while (controller.isRecoveryOngoing()) {
            String recoveryAction = controller.getRecoveryAction();
            Map<String, String> subsytemRecoveryActions = controller.getSubsystemRecoveryActions();

            System.out.println(recoveryAction);
            System.out.println(subsytemRecoveryActions);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException intEx) {
                intEx.printStackTrace();
                break;
            }
        }

        recoveryRequest.setFinished(new Date());
        recoveryJobRepository.save(recoveryRequest);
    }



}
