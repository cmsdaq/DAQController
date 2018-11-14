package ch.cern.cms.daq.expertcontroller.service.recoveryservice;

import ch.cern.cms.daq.expertcontroller.entity.Event;
import ch.cern.cms.daq.expertcontroller.service.recoveryservice.fsm.State;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
/**
 * TODO: decide if this should include information about currently executed procedure (I think no, this should be kept simple POJO)
 */
public class ExecutorStatus {

    private State state;

    private List<Event> actionSummary;
}
