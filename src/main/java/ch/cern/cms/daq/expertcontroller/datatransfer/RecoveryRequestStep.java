package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryRequestStep {

    Integer stepIndex;

    String humanReadable;

    Boolean issueTTCHardReset;

    Set<String> redRecycle;

    Set<String> greenRecycle;

    Set<String> fault;

    Set<String> reset;

}
