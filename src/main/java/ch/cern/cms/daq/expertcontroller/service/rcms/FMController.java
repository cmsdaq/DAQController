package ch.cern.cms.daq.expertcontroller.service.rcms;

import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.bean.FunctionManagerParameterBean;
import rcms.fm.fw.parameter.type.MapT;
import rcms.fm.fw.parameter.type.ParameterType;
import rcms.fm.fw.parameter.util.ParameterUtil;
import rcms.fm.fw.service.parameter.ParameterRelayRemote;
import rcms.fm.fw.service.parameter.ParameterServiceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FMController {


    protected final String senderURI;

    protected List<String> URIList = new ArrayList<String>();
    protected String[] URIs = null;


    protected ParameterRelayRemote parameterRelay;

    /**
     * Constructs a new controller.
     *
     * @param senderURI
     *            The URI that is to be used as sender URI in requests to the
     *            LV0A.
     * @throws LV0AutomatorControlException
     *             If the parameter relay could not be created.
     */
    public FMController(String senderURI) throws LV0AutomatorControlException {
        this.senderURI = senderURI;

        try {
            this.parameterRelay = new ParameterRelayRemote();
        } catch (ParameterServiceException psEx) {
            throw new LV0AutomatorControlException("Exception creating parameter relay.", psEx);
        }
    }

    protected static FunctionManagerParameterBean mapToBean(Map<String, ?> map) {
        MapT<ParameterType<?>> mapT = MapT.createFromMap(map);
        FunctionManagerParameter<ParameterType> parameter = new FunctionManagerParameter<ParameterType>("GUI_COMMAND",
                mapT);

        return ParameterUtil.transform(parameter);
    }

    /**
     * Adds an URI to the controller, making it receive commands issued using
     * the controller.
     */
    public void addURI(String URI) {
        this.URIList.add(URI);
        this.URIs = this.URIList.toArray(new String[this.URIList.size()]);
    }

}
