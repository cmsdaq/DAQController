package ch.cern.cms.daq.expertcontroller.rcmsController;

import rcms.fm.fw.parameter.bean.FunctionManagerParameterBean;
import rcms.fm.fw.service.command.CommandRelayRemote;
import rcms.fm.fw.service.command.CommandServiceException;
import rcms.statemachine.definition.bean.CommandBean;

public class L0Controller extends FMController {


    private CommandRelayRemote commandRelayRemote;

    /**
     * Constructs a new controller.
     *
     * @param senderURI The URI that is to be used as sender URI in requests to the LV0A.
     * @throws LV0AutomatorControlException If the parameter relay could not be created.
     */
    public L0Controller(String senderURI) throws LV0AutomatorControlException {
        super(senderURI);

        try {
            commandRelayRemote = new CommandRelayRemote();
        } catch (CommandServiceException e) {
            e.printStackTrace();
        }
    }

    private static CommandBean buildTTCHardResetBean() {

        CommandBean commandBean = new CommandBean();
        commandBean.setCommandString("TTCHardReset");
        return commandBean;
    }

    public boolean sendTTCHardReset() throws LV0AutomatorControlException {

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            this.commandRelayRemote.execute(this.URIs, buildTTCHardResetBean(), this.senderURI);
            return true;
        } catch (CommandServiceException e) {
            e.printStackTrace();
            return false;
        }


    }
}
