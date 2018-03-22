package ch.cern.cms.daq.expertcontroller.rcmsController;

import java.util.HashMap;
import java.util.Map;

import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.ParameterException;
import rcms.fm.fw.parameter.bean.FunctionManagerParameterBean;
import rcms.fm.fw.parameter.type.MapT;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.parameter.util.ParameterUtil;
import rcms.fm.fw.service.parameter.ParameterRelayRemote;
import rcms.fm.fw.service.parameter.ParameterServiceException;

/**
 * Example usage of the LV0A controller.
 *
 * For this to work, the controlled LV0A instance needs ANYFM_ACCESS enabled.
 */
public class LV0AutomatorControllerTest {

    private static final String AUTOMATOR_URI = "http://dvbu-pcintelsz.cern.ch:18080/urn:rcms-fm:fullpath=/philipp/pccms87/rcms42_port8080/lvl0FMwithAutomator,group=lvl0A,owner=philipp";
    private static final String[] L0_URI = {"http://dvbu-pcintelsz.cern.ch:18080/urn:rcms-fm:fullpath=/philipp/pccms87/rcms42_port8080/lvl0FMwithAutomator,group=lvl0,owner=philipp"};

    public static void main(String[] args) {

        try {
            // create controller with sender address
            LV0AutomatorController controller = new LV0AutomatorController("lv0a-controller.local");

            // set controller target LV0A instance(s)
            controller.addAutomatorURI(AUTOMATOR_URI);

            // interrupt the currently ongoing recovery
            controller.interruptRecovery();

            // set schedule for a single subsystem
            controller.setSchedule("DAQ", LV0AutomatorController.SUBSYSTEM_SCHEDULE_RECYCLE);

            System.out.println(controller.getSchedule());

            // set schedule for multiple subsystems
            Map<String, String> schedules = new HashMap<>();
            schedules.put("PIXEL", LV0AutomatorController.SUBSYSTEM_SCHEDULE_RECONFIGURE);
            schedules.put("TRACKER", LV0AutomatorController.SUBSYSTEM_SCHEDULE_RECONFIGURE);
            schedules.put("ECAL", LV0AutomatorController.SUBSYSTEM_SCHEDULE_RECYCLE);
            controller.setSchedules(schedules);

            System.out.println(controller.getSchedule());

            // reset schedule for single subsystem
            controller.setSchedule("TRACKER", LV0AutomatorController.SUBSYSTEM_SCHEDULE_NONE);

            System.out.println(controller.getSchedule());

            // set fault for a single subsystem
            controller.setFault("DAQ", true);

            System.out.println(controller.getFaults());

            // set faults for multiple subsystems
            Map<String, Boolean> faults = new HashMap<>();
            faults.put("PIXEL", true);
            faults.put("TRACKER", true);
            faults.put("ECAL", true);
            controller.setFaults(faults);

            System.out.println(controller.getFaults());

            // reset fault for single subsystem
            controller.setFault("TRACKER", false);

            System.out.println(controller.getFaults());

            // start a recovery
            controller.startRecovery();


            for(int i = 10; i > 0; i--){
                System.out.println("Will interrupt recovery in " + i + " seconds");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException intEx) {
                    intEx.printStackTrace();
                    break;
                }
            }

            // interrupt own recovery
            controller.interruptRecovery();
            System.out.println("Recovery interrupted");

            // start a run
            // controller.startRun();

			/*
			 * Print out current recovery action and subsystem recovery actions
			 * as long as the recovery is ongoing.
			 */
            while (controller.isRecoveryOngoing()) {
                String recoveryAction = controller.getRecoveryAction();
                Map<String, String> subsytemRecoveryActions = controller.getSubsystemRecoveryActions();

                System.out.println(recoveryAction);
                System.out.println(subsytemRecoveryActions);

                System.out.println();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException intEx) {
                    intEx.printStackTrace();
                    break;
                }
            }
            FunctionManagerParameter parameter = new FunctionManagerParameter<MapT>("TRACKER_STATUS",
                    new MapT());
            FunctionManagerParameterBean[] subsystemState = {ParameterUtil.transform(parameter)};

            ParameterRelayRemote parameterRelay = null;
            try {
                parameterRelay = new ParameterRelayRemote();

                FunctionManagerParameterBean[] parameterBeans = parameterRelay.getParameter(L0_URI,
                        subsystemState, "philipplion");


                MapT mapt = ((MapT)(ParameterUtil.transform(parameterBeans[0]).getValue()));
                Object value = mapt.getMap().get(new StringT("STATE"));
                String valueStr = value.toString();
                System.out.println("V: " + valueStr);

            } catch (ParameterServiceException e) {
                e.printStackTrace();
            } catch (ParameterException e) {
                e.printStackTrace();
            }



        } catch (LV0AutomatorControlException cEx) {
            cEx.printStackTrace();
        }
    }

    public void getStatus(){

    }

}
