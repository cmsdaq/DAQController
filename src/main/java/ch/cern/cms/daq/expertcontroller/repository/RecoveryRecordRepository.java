package ch.cern.cms.daq.expertcontroller.repository;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource
public interface RecoveryRecordRepository extends JpaRepository<RecoveryRecord, Long> {

    Optional<RecoveryRecord> findById(@Param("id") Long id);

    /**
     * Will return the Recovery records in given time span
     */
    @Query("select r from RecoveryRecord r where r.start <= :endDate and (r.end >= :startDate or r.end is null)")
    List<RecoveryRecord> findBetween(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

}