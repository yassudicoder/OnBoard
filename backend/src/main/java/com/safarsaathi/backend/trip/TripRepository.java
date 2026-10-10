package com.safarsaathi.backend.trip;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and saves {@link Trip} rows. There is no code to write: Spring Data creates the class behind this interface
 * at startup, with ready-made methods such as {@code findById(id)}, {@code save(trip)} and {@code count()}.
 * {@code <Trip, Long>} = the entity and the type of its id.
 */
public interface TripRepository extends JpaRepository<Trip, Long> {
}
