package ch.cern.cms.daq.expertcontroller.datatransfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryRequestDTO {

    private Long problemId;

    private String status;

    private boolean withInterrupt;

    private boolean isSameProblem;

    private boolean withPostponement;

    private List<RecoveryRequestStepDTO> recoverySteps;

    String problemTitle;

    String problemDescription;

    Date received;

}
