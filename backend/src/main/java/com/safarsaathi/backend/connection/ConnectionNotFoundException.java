package com.safarsaathi.backend.connection;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * "There is no connection with this id": answered as 404 Problem Details, like TripNotFoundException, e.g.
 * {@code {"title":"Not Found","status":404,"detail":"Connection 99 not found", ...}}.
 */
public class ConnectionNotFoundException extends ErrorResponseException {

    public ConnectionNotFoundException(long connectionId) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Connection " + connectionId + " not found"),
                null);
    }
}
