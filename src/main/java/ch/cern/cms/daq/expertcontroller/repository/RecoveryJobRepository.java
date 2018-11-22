package ch.cern.cms.daq.expertcontroller.repository;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryJob;
import ch.cern.cms.daq.expertcontroller.entity.RecoveryProcedure;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource
public interface RecoveryJobRepository extends JpaRepository<RecoveryJob, Long> {
    Optional<RecoveryJob> findById(@Param("id") Long id);

}