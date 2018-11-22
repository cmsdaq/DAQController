package ch.cern.cms.daq.expertcontroller.repository;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource
public interface RecoveryProcedureRepository extends JpaRepository<RecoveryProcedure, Long> {
    Optional<RecoveryProcedure> findById(@Param("id") Long id);


    /**
     * Will return the Recovery procedures in given time window
     */
    @Query("select r from RecoveryProcedure r where r.start <= :endDate and (r.end >= :startDate or r.end is null)")
    List<RecoveryProcedure> findBetween(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Sort sort);


    List<RecoveryProcedure> findTop20ByOrderByStartDesc();
}