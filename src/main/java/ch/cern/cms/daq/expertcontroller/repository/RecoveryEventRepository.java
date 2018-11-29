package ch.cern.cms.daq.expertcontroller.repository;

import ch.cern.cms.daq.expertcontroller.entity.RecoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

@RepositoryRestResource
public interface RecoveryEventRepository extends JpaRepository<RecoveryEvent, Long> {
    Optional<RecoveryEvent> findById(@Param("id") Long id);

}