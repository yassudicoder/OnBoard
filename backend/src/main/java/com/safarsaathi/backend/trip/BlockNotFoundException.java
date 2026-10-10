package com.safarsaathi.backend.trip;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * "This trip has no block with this id": the block does not exist at all, or it belongs to another trip.
 * Answered as 404 Not Found with a Problem Details JSON body, exactly like {@link TripNotFoundException}, e.g.
 * {@code "detail": "Block 7 not found in trip 1"}.
 */
public class BlockNotFoundException extends ErrorResponseException {

    public BlockNotFoundException(long tripId, long blockId) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                        "Block " + blockId + " not found in trip " + tripId),
                null);
    }
}
