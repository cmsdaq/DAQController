package ch.cern.cms.daq.expertcontroller.service;

import ch.cern.cms.daq.expertcontroller.datatransfer.RecoveryRecord;
import ch.cern.cms.daq.expertcontroller.repository.RecoveryProcedureRepository;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecoveryRecordService {

    @Autowired
    private RecoveryProcedureRepository recoveryProcedureRepository;

    public List<RecoveryRecord> getRecords(ZonedDateTime start, ZonedDateTime end) {

        List<RecoveryProcedure> procedures = recoveryProcedureRepository.findBetween(start, end);

        List<RecoveryRecord> records = new ArrayList<>();

        procedures.stream().forEach(p -> {

            RecoveryRecord procedureRecord = RecoveryRecord.builder()
                    .start(Date.from(p.getStart().toInstant()))
                    .end(Date.from(p.getEnd().toInstant()))
                    .name("Recovery procedure #" + p.getId())
                    .description("" + p.getProblemTitle())
                    .build();

            List<RecoveryRecord> jobRecords = p.getExecutedJobs().stream()
                    .map(j -> RecoveryRecord.builder()
                            .start(Date.from(j.getStart().toInstant()))
                            .end(Date.from(j.getEnd().toInstant()))
                            .name("Job #" + j.getId())
                            .description("" + j.getJob())
                            .build()

                    ).collect(Collectors.toList());

            records.add(procedureRecord);
            records.addAll(jobRecords);

        });


        return records;


    }
}
