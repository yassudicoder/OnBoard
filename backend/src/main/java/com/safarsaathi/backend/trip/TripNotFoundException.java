package com.safarsaathi.backend.trip;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * "There is no trip with this id". Throw it anywhere while answering a request, and the client gets a
 * 404 Not Found with a Problem Details JSON body (Content-Type application/problem+json), for example:
 * <pre>
 * {"type":"about:blank","title":"Not Found","status":404,"detail":"Trip 99 not found","instance":"/api/trips/99"}
 * </pre>
 *
 * <p>No extra error-handling code is needed: {@link ErrorResponseException} is Spring's own "error that already knows
 * its HTTP answer", and Spring Boot's Problem Details handler (spring.mvc.problemdetails.enabled in application.yml)
 * turns it into that JSON. No stack trace ever reaches the client.
 */
public class TripNotFoundException extends ErrorResponseException {

    public TripNotFoundException(long tripId) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Trip " + tripId + " not found"),
                null);
    }
}
