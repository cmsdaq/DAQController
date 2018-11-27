package ch.cern.cms.daq.expertcontroller.service;

import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRecord;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import ch.cern.cms.daq.expertcontroller.repository.RecoveryProcedureRepository;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecoveryRecordService {

    @Autowired
    private RecoveryProcedureRepository recoveryProcedureRepository;

    @Autowired
    private IRecoveryService iRecoveryService;

    public List<RecoveryRecord> getRecords(OffsetDateTime start, OffsetDateTime end) {

        List<RecoveryProcedure> procedures = recoveryProcedureRepository.findBetween(start, end, new Sort("start"));

        List<RecoveryRecord> records = new ArrayList<>();

        procedures.stream().forEach(p -> {

            RecoveryRecord procedureRecord = RecoveryRecord.builder()
                    .id("p-" + p.getId())
                    .start(p.getStart())
                    .end(p.getEnd())
                    .name("Recovery procedure #" + p.getId())
                    .description("" + p.getProblemTitle())
                    .build();

            List<RecoveryRecord> jobRecords = p.getExecutedJobs().stream()
                    .map(j -> RecoveryRecord.builder()
                            .id("j-" + j.getId())
                            .start(j.getStart())
                            .end(j.getEnd())
                            .name("Job #" + j.getId())
                            .description("" + j.getJob())
                            .build()

                    ).collect(Collectors.toList());

            records.add(procedureRecord);
            records.addAll(jobRecords);

        });


        return records;

    }

    public List<ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryProcedure> getProcedures(OffsetDateTime start, OffsetDateTime end) {
        List<RecoveryProcedure> procedureList =
                recoveryProcedureRepository.findBetween(start, end, new Sort(Sort.Direction.DESC, "start"));


        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT);
        List<ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryProcedure> result = modelMapper.map(procedureList, List.class);


        return result;
    }
}
