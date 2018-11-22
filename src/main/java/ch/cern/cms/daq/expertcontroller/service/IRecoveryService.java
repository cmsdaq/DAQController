package ch.cern.cms.daq.expertcontroller.service;

import ch.cern.cms.daq.expertcontroller.datatransfer.*;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

/**
 * Interface of te service for managing the whole recovery process
 */
@Service
public interface IRecoveryService {


    /**
     * Submit recovery request to process by controller
     *
     * @param recoveryRequest recovery request domain object
     * @return recovery response data transfer object
     */
    RecoveryResponse submitRecoveryRequest(RecoveryRequest recoveryRequest);

    /**
     * Submit approval response for given recovery request.
     *
     * @param approvalResponse approval response data transfer object
     * @return approval acceptanceDecision
     */
    String submitApprovalDecision(ApprovalResponse approvalResponse);


    /**
     * Get finalStatus of given recovery procedure
     *
     * @param id of the recovery procedure
     * @return finalStatus of given recovery procedure
     */
    RecoveryProcedureStatus getRecoveryProcedureStatus(Long id);

    /**
     * Gets finalStatus of recovery service
     *
     * @return finalStatus of the recovery service
     */
    RecoveryServiceStatus getRecoveryServiceStatus();

    /**
     * Problem with given id has completed
     *
     * @param id id of problem that finished
     */
    void finished(Long id);

    InterruptResponse interrupt();


    void onRecoveryProcedureStateUpdate();

    void onApprovalRequest(ApprovalRequest approvalRequest);

    /**
     * Closes the service gracefully before shutdown
     */
    @PreDestroy
    void shutdown();


}
