package com.safarsaathi.backend.airport;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * "We have no map for this terminal": a 404 Problem Details answer, like TripNotFoundException. The pilot only has
 * Mumbai Terminal 2 (CLAUDE.md).
 */
public class AirportMapNotFoundException extends ErrorResponseException {

    public AirportMapNotFoundException(String airport, String terminal) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                        "No map for airport " + airport + " terminal " + terminal + " (the pilot has BOM T2 only)"),
                null);
    }
}
