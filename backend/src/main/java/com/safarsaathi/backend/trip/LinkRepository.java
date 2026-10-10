package com.safarsaathi.backend.trip;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and saves {@link Link} rows (Spring Data writes the code, see {@link TripRepository}).
 * The method name means "the links with this trip id, in id order".
 */
public interface LinkRepository extends JpaRepository<Link, Long> {

    List<Link> findByTripIdOrderByIdAsc(Long tripId);
}
