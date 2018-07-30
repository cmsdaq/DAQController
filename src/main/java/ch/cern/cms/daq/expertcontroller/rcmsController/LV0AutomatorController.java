package ch.cern.cms.daq.expertcontroller.rcmsController;

import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.Parameter;
import rcms.fm.fw.parameter.ParameterException;
import rcms.fm.fw.parameter.bean.FunctionManagerParameterBean;
import rcms.fm.fw.parameter.type.*;
import rcms.fm.fw.parameter.util.ParameterUtil;
import rcms.fm.fw.service.parameter.ParameterRelayRemote;
import rcms.fm.fw.service.parameter.ParameterServiceException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple controller class for the automator.
 * 
 * Makes use of the RCMS framework's ParameterRelayRemote class.
 */
@SuppressWarnings("rawtypes")
public class LV0AutomatorController extends FMController {

	public static final String SUBSYSTEM_SCHEDULE_RECYCLE = "Recycle";
	public static final String SUBSYSTEM_SCHEDULE_RECONFIGURE = "Reconfigure";
	public static final String SUBSYSTEM_SCHEDULE_NONE = "None";


	public LV0AutomatorController(String senderURI) throws LV0AutomatorControlException{
		super(senderURI);
	}

	private static FunctionManagerParameterBean buildStartRecoveryBean() {
		Map<String, String> requestMap = new HashMap<>();
		requestMap.put("command", "startRecovery");

		return mapToBean(requestMap);
	}

	private static final FunctionManagerParameterBean[] startRecoveryBean = { buildStartRecoveryBean() };

	/**
	 * Starts a recovery.
	 * 
	 * @throws LV0AutomatorControlException
	 *             If there was an error setting the GUI_COMMAND parameter.
	 */
	public void startRecovery() throws LV0AutomatorControlException {
		try {
			this.parameterRelay.setParameter(this.URIs, startRecoveryBean, this.senderURI);
		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception setting parameter.", psEx);
		}
	}

	private static FunctionManagerParameterBean buildStartRunBean() {
		Map<String, String> requestMap = new HashMap<>();
		requestMap.put("command", "startRun");

		return mapToBean(requestMap);
	}

	private static final FunctionManagerParameterBean[] startRunBean = { buildStartRunBean() };

	/**
	 * Starts a run.
	 * 
	 * @throws LV0AutomatorControlException
	 *             If there was an error setting the GUI_COMMAND parameter.
	 */
	public void startRun() throws LV0AutomatorControlException {
		try {
			this.parameterRelay.setParameter(this.URIs, startRunBean, this.senderURI);

		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception setting parameter.", psEx);
		}
	}

	private static FunctionManagerParameterBean buildInterruptRecoveryBean() {
		Map<String, String> requestMap = new HashMap<>();
		requestMap.put("command", "interruptRecovery");

		return mapToBean(requestMap);
	}

	private static final FunctionManagerParameterBean[] interruptRecoveryBean = { buildInterruptRecoveryBean() };

	/**
	 * Interrupts an ongoing recovery.
	 * 
	 * @throws LV0AutomatorControlException
	 *             If there was an error setting the GUI_COMMAND parameter.
	 */
	public void interruptRecovery() throws LV0AutomatorControlException {
		try {
			this.parameterRelay.setParameter(this.URIs, interruptRecoveryBean, this.senderURI);
		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception setting parameter.", psEx);
		}
	}

	private static final FunctionManagerParameterBean buildSetScheduleBean(String subsystemName, String schedule) {
		Map<String, String> requestMap = new HashMap<>();
		requestMap.put("command", "subsystemSchedule");
		requestMap.put("system", subsystemName);
		requestMap.put("schedule", schedule);

		return mapToBean(requestMap);
	}

	/**
	 * Sets the schedule of a single subsystem.
	 * 
	 * @param subsystemName
	 *            The subsystem's name.
	 * @param schedule
	 *            The schedule to be set.
	 * @throws LV0AutomatorControlException
	 *             If there was an error setting the GUI_COMMAND parameter.
	 */
	public void setSchedule(String subsystemName, String schedule) throws LV0AutomatorControlException {
		FunctionManagerParameterBean[] setScheduleBean = { buildSetScheduleBean(subsystemName, schedule) };
		try {
			this.parameterRelay.setParameter(this.URIs, setScheduleBean, this.senderURI);
		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception setting parameter.", psEx);
		}
	}

	private static final FunctionManagerParameterBean buildSetSchedulesBean(Map<String, String> schedules) {
		Map<String, Object> requestMap = new HashMap<>();
		requestMap.put("command", "subsystemSchedules");
		requestMap.put("schedules", schedules);

		return mapToBean(requestMap);
	}

	/**
	 * Sets the schedule of multiple subsystems.
	 * 
	 * @param schedules
	 *            A mapping of the format <code>subsystemName => schedule</code>
	 *            describing the schedule to be set.
	 * @throws LV0AutomatorControlException
	 *             If there was an error setting the GUI_COMMAND parameter.
	 */
	public void setSchedules(Map<String, String> schedules) throws LV0AutomatorControlException {
		FunctionManagerParameterBean[] setScheduleBean = { buildSetSchedulesBean(schedules) };
		try {
			this.parameterRelay.setParameter(this.URIs, setScheduleBean, this.senderURI);
		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception setting parameter.", psEx);
		}
	}

	private static final FunctionManagerParameterBean buildSetFaultBean(String subsystemName, boolean atFault) {
		Map<String, String> requestMap = new HashMap<>();
		requestMap.put("command", "subsystemFault");
		requestMap.put("system", subsystemName);
		requestMap.put("atFault", Boolean.toString(atFault));

		return mapToBean(requestMap);
	}

	/**
	 * Sets the schedule of a single subsystem.
	 * 
	 * @param subsystemName
	 *            The subsystem's name.
	 * @param atFault
	 *            The fault to be set.
	 * @throws LV0AutomatorControlException
	 *             If there was an error setting the GUI_COMMAND parameter.
	 */
	public void setFault(String subsystemName, boolean atFault) throws LV0AutomatorControlException {
		FunctionManagerParameterBean[] setFaultBean = { buildSetFaultBean(subsystemName, atFault) };
		try {
			this.parameterRelay.setParameter(this.URIs, setFaultBean, this.senderURI);
		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception setting parameter.", psEx);
		}
	}

	private static final FunctionManagerParameterBean buildSetFaultsBean(Map<String, Boolean> faults) {
		Map<String, Object> requestMap = new HashMap<>();
		requestMap.put("command", "subsystemFaults");
		requestMap.put("faults", faults);

		return mapToBean(requestMap);
	}

	/**
	 * Sets the schedule of multiple subsystems.
	 *
	 *            A mapping of the format <code>subsystemName => schedule</code>
	 *            describing the schedule to be set.
	 * @throws LV0AutomatorControlException
	 *             If there was an error setting the GUI_COMMAND parameter.
	 */
	public void setFaults(Map<String, Boolean> faults) throws LV0AutomatorControlException {
		FunctionManagerParameterBean[] setScheduleBean = { buildSetFaultsBean(faults) };
		try {
			this.parameterRelay.setParameter(this.URIs, setScheduleBean, this.senderURI);
		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception setting parameter.", psEx);
		}
	}

	private static Map<String, String> beanToStringMap(FunctionManagerParameterBean bean)
			throws LV0AutomatorControlException {
		try {
			Parameter<?> schedulesParameter = ParameterUtil.transform(bean);
			if (schedulesParameter.getEnumType() == ParameterTypesEnum.MAP_T) {
				MapT<?> schedules = (MapT<?>) schedulesParameter.getValue();

				Map<String, String> scheduleMap = new HashMap<>();

				for (Map.Entry<StringT, ?> schedule : schedules.getMap().entrySet()) {
					scheduleMap.put(schedule.getKey().getString(), schedule.getValue().toString());
				}

				return scheduleMap;
			} else {
				throw new LV0AutomatorControlException("Returned subsystem schedule parameter has an unexpected type.");
			}
		} catch (ParameterException pEx) {
			throw new LV0AutomatorControlException("Exception transforming parameter.", pEx);
		}
	}

	private static Map<String, Boolean> beanToBooleanMap(FunctionManagerParameterBean bean)
			throws LV0AutomatorControlException {
		try {
			Parameter<?> schedulesParameter = ParameterUtil.transform(bean);
			if (schedulesParameter.getEnumType() == ParameterTypesEnum.MAP_T) {
				MapT<?> schedules = (MapT<?>) schedulesParameter.getValue();

				Map<String, Boolean> scheduleMap = new HashMap<>();

				for (Map.Entry<StringT, ?> schedule : schedules.getMap().entrySet()) {
					scheduleMap.put(schedule.getKey().getString(), ((BooleanT) schedule.getValue()).getBoolean());
				}

				return scheduleMap;
			} else {
				throw new LV0AutomatorControlException("Returned subsystem schedule parameter has an unexpected type.");
			}
		} catch (ParameterException pEx) {
			throw new LV0AutomatorControlException("Exception transforming parameter.", pEx);
		}
	}

	private static FunctionManagerParameterBean buildScheduleBean() {
		FunctionManagerParameter parameter = new FunctionManagerParameter<MapT>("SUBSYSTEM_SCHEDULE", new MapT<>());
		return ParameterUtil.transform(parameter);
	}

	private static final FunctionManagerParameterBean[] scheduleBean = { buildScheduleBean() };

	/**
	 * @return The current automator schedule by subsystem.
	 * @throws LV0AutomatorControlException
	 *             If there was a problem retrieving the parameter.
	 */
	public Map<String, String> getSchedule() throws LV0AutomatorControlException {
		try {
			FunctionManagerParameterBean[] parameterBeans = this.parameterRelay.getParameter(this.URIs, scheduleBean,
					this.senderURI);
			if (parameterBeans.length > 0) {
				return beanToStringMap(parameterBeans[0]);
			} else {
				throw new LV0AutomatorControlException("No parameter returned.");
			}
		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception getting parameter.", psEx);
		}
	}

	private static FunctionManagerParameterBean buildFaultBean() {
		FunctionManagerParameter parameter = new FunctionManagerParameter<MapT>("SUBSYSTEM_FAULTS", new MapT<>());
		return ParameterUtil.transform(parameter);
	}

	private static final FunctionManagerParameterBean[] faultBean = { buildFaultBean() };

	/**
	 * @return The current automator faults by subsystem.
	 * @throws LV0AutomatorControlException
	 *             If there was a problem retrieving the parameter.
	 */
	public Map<String, Boolean> getFaults() throws LV0AutomatorControlException {
		try {
			FunctionManagerParameterBean[] parameterBeans = this.parameterRelay.getParameter(this.URIs, faultBean,
					this.senderURI);
			if (parameterBeans.length > 0) {
				return beanToBooleanMap(parameterBeans[0]);
			} else {
				throw new LV0AutomatorControlException("No parameter returned.");
			}
		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception getting parameter.", psEx);
		}
	}

	private static FunctionManagerParameterBean buildRecoveryActionBean() {
		FunctionManagerParameter parameter = new FunctionManagerParameter<StringT>("RECOVERY_ACTION", new StringT(""));
		return ParameterUtil.transform(parameter);
	}

	private static final FunctionManagerParameterBean[] recoveryActionBean = { buildRecoveryActionBean() };

	/**
	 * @return The currently ongoing (global) recovery action.
	 * @throws LV0AutomatorControlException
	 *             If there was a problem retrieving the parameter.
	 */
	public String getRecoveryAction() throws LV0AutomatorControlException {
		try {
			FunctionManagerParameterBean[] parameterBeans = this.parameterRelay.getParameter(this.URIs,
					recoveryActionBean, this.senderURI);
			if (parameterBeans.length > 0) {
				return ParameterUtil.transform(parameterBeans[0]).getValue().toString();
			} else {
				throw new LV0AutomatorControlException("No parameter returned.");
			}
		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception getting parameter.", psEx);
		} catch (ParameterException pEx) {
			throw new LV0AutomatorControlException("Exception transforming parameter.", pEx);
		}
	}

	private static FunctionManagerParameterBean buildSubsystemRecoveryActionsBean() {
		FunctionManagerParameter parameter = new FunctionManagerParameter<MapT>("SUBSYSTEM_RECOVERY_ACTIONS",
				new MapT<>());
		return ParameterUtil.transform(parameter);
	}

	private static final FunctionManagerParameterBean[] subsystemRecoveryActionsBean = {
			buildSubsystemRecoveryActionsBean() };

	/**
	 * @return Currently ongoing subsystem recovery actions by subsystem.
	 * @throws LV0AutomatorControlException
	 *             If there was a problem retrieving the parameter.
	 */
	public Map<String, String> getSubsystemRecoveryActions() throws LV0AutomatorControlException {
		try {
			FunctionManagerParameterBean[] parameterBeans = this.parameterRelay.getParameter(this.URIs,
					subsystemRecoveryActionsBean, this.senderURI);
			if (parameterBeans.length > 0) {
				return beanToStringMap(parameterBeans[0]);
			} else {
				throw new LV0AutomatorControlException("No parameter returned.");
			}
		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception getting parameter.", psEx);
		}
	}

	private static FunctionManagerParameterBean buildRecoveryOngoingsBean() {
		FunctionManagerParameter parameter = new FunctionManagerParameter<BooleanT>("RECOVERY_ONGOING",
				new BooleanT(false));
		return ParameterUtil.transform(parameter);
	}

	private static final FunctionManagerParameterBean[] recoveryOngoingBean = { buildRecoveryOngoingsBean() };

	/**
	 * @return Whether or not a recovery is currently ongoing.
	 * @throws LV0AutomatorControlException
	 *             If there was a problem retrieving the parameter.
	 */
	public boolean isRecoveryOngoing() throws LV0AutomatorControlException {
		try {
			FunctionManagerParameterBean[] parameterBeans = this.parameterRelay.getParameter(this.URIs,
					recoveryOngoingBean, this.senderURI);
			if (parameterBeans.length > 0) {
				return Boolean.parseBoolean(parameterBeans[0].getValue().toString());
			} else {
				throw new LV0AutomatorControlException("No parameter returned.");
			}
		} catch (ParameterServiceException psEx) {
			throw new LV0AutomatorControlException("Exception getting parameter.", psEx);
		}
	}

}
