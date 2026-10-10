package com.safarsaathi.backend.trip;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Reads and saves {@link Trip} rows. There is no code to write: Spring Data creates the class behind this interface
 * at startup, with ready-made methods such as {@code findById(id)}, {@code save(trip)} and {@code count()}.
 * {@code <Trip, Long>} = the entity and the type of its id.
 */
public interface TripRepository extends JpaRepository<Trip, Long> {

    /**
     * Reads the trip AND locks its row until the current transaction ends ("SELECT ... FOR UPDATE").
     * Anyone else asking for the same lock waits until then. Use it before changing a trip's plan, so two changes
     * to one trip happen one after the other: the second one reads the first one's result (version, times)
     * instead of overwriting it. Must be called inside a transaction ({@code @Transactional}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") long id);
}
