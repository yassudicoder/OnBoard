package com.safarsaathi.backend.trip;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads trips for the API. Changes to trips (delays, cancellations) are saved by
 * {@code com.safarsaathi.backend.watcher.DisruptionService}.
 */
@Service
public class TripService {

    private final TripRepository tripRepository;
    private final BlockRepository blockRepository;
    private final LinkRepository linkRepository;
    private final TripMapper mapper;

    public TripService(TripRepository tripRepository, BlockRepository blockRepository,
                       LinkRepository linkRepository, TripMapper mapper) {
        this.tripRepository = tripRepository;
        this.blockRepository = blockRepository;
        this.linkRepository = linkRepository;
        this.mapper = mapper;
    }

    /**
     * The trip with all its blocks (earliest start first, then by id) and links (by id).
     *
     * <p>{@code @Transactional(readOnly = true)}: the three reads below run in one database transaction.
     * REPEATABLE_READ makes all three see the database as it was at the first read, so the trip's {@code version}
     * always belongs to exactly these blocks, even if a disruption is saved at the same moment. That matters
     * because the app decides by {@code version} whether to replace its offline copy.
     *
     * @throws TripNotFoundException (answered as 404) when there is no trip with this id
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TripResponse getTrip(long tripId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(() -> new TripNotFoundException(tripId));
        List<Block> blocks = blockRepository.findByTripIdOrderByStartTimeAscIdAsc(tripId);
        List<Link> links = linkRepository.findByTripIdOrderByIdAsc(tripId);
        return mapper.toResponse(trip, blocks, links);
    }
}
