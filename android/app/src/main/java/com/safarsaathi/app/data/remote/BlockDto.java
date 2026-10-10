package com.safarsaathi.app.data.remote;

/** One block inside {@link TripDto} (JSON field names, see docs/api.md). */
public class BlockDto {
    public Long id;
    public Long tripId;
    public String type;
    public String title;
    public String startTime;
    public String endTime;
    public String locationName;
    public Double lat;
    public Double lng;
    public String provider;
    public String providerRef;
    public String flex;
    public Boolean anchor;
    public String status;
    public Integer freeChangeUntilMinutes;
    public Integer changeFeeInr;
    public String notes;
}
