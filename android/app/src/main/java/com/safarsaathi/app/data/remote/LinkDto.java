package com.safarsaathi.app.data.remote;

/** One link inside {@link TripDto} (JSON field names, see docs/api.md). */
public class LinkDto {
    public Long id;
    public Long tripId;
    public Long fromBlockId;
    public Long toBlockId;
    public Integer minGapMinutes;
    public Integer travelMinutes;
    public String travelMode;
}
