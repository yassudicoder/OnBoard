package com.safarsaathi.backend.planb;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and saves {@link RecoveryPlan} rows (Spring Data writes the code). At most one plan per connection. */
public interface RecoveryPlanRepository extends JpaRepository<RecoveryPlan, Long> {

    Optional<RecoveryPlan> findByConnectionId(Long connectionId);
}
