package com.safarsaathi.app.data.remote;

import java.util.List;

/**
 * The JSON of {@code GET /api/trips/{id}}, field for field (Gson fills these in by matching names).
 * A "DTO" (data transfer object) is only the shape of the message; TripMapper turns it into Room rows.
 *
 * <p>Numbers are boxed (Long, Integer) so a missing value stays null and TripMapper can complain about it
 * instead of silently using 0. Times stay Strings such as "2026-10-11T08:00:00+05:30" here; TripMapper
 * parses them.
 */
public class TripDto {
    public Long id;
    public String title;
    public String purpose;
    public String persona;
    public String language;
    public Integer travellers;
    public String status;
    public Integer version;
    public String createdAt;
    public String updatedAt;
    public List<BlockDto> blocks;
    public List<LinkDto> links;
}
