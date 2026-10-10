package com.safarsaathi.backend.trip;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and saves {@link DisruptionEvent} rows (Spring Data writes the code, see {@link TripRepository}).
 * The method name means "the events of this trip, oldest first".
 */
public interface DisruptionEventRepository extends JpaRepository<DisruptionEvent, Long> {

    List<DisruptionEvent> findByTripIdOrderByIdAsc(Long tripId);

    /** True when this block of this trip already has an event of this kind (used to record LIKELY_MISSED only once). */
    boolean existsByTripIdAndBlockIdAndKind(Long tripId, Long blockId, DisruptionKind kind);
}
