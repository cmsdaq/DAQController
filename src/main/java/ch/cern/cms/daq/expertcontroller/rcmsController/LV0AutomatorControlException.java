package ch.cern.cms.daq.expertcontroller.rcmsController;

public class LV0AutomatorControlException extends Exception {

	private static final long serialVersionUID = 1L;

	public LV0AutomatorControlException() {
		super();
	}

	public LV0AutomatorControlException(String message) {
		super(message);
	}

	public LV0AutomatorControlException(String message, Throwable cause) {
		super(message, cause);
	}

	public LV0AutomatorControlException(Throwable cause) {
		super(cause);
	}
}
