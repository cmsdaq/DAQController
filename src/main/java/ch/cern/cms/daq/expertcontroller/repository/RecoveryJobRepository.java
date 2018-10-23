package ch.cern.cms.daq.expertcontroller.repository;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

@RepositoryRestResource
public interface RecoveryJobRepository extends JpaRepository<RecoveryRequest, Long> {
    Optional<RecoveryRequest> findById(@Param("id") Long id);

}