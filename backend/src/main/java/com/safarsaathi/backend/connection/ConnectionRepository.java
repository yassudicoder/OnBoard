package com.safarsaathi.backend.connection;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and saves {@link Connection} rows (Spring Data writes the code). "The trip's connections, in id order." */
public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    List<Connection> findByTripIdOrderByIdAsc(Long tripId);
}
