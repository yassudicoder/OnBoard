package com.safarsaathi.backend.trip;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and saves {@link Block} rows (Spring Data writes the code, see {@link TripRepository}).
 *
 * <p>Spring Data builds the SQL for the method below from its NAME:
 * {@code findBy TripId OrderBy StartTime Asc Id Asc} = "the blocks with this trip id, earliest start first;
 * blocks that start at the same moment in id order". The fixed order matters: the API and the app show blocks
 * exactly in this order.
 */
public interface BlockRepository extends JpaRepository<Block, Long> {

    List<Block> findByTripIdOrderByStartTimeAscIdAsc(Long tripId);
}
