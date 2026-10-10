package com.safarsaathi.app.data.ride;

import androidx.annotation.Nullable;

/**
 * One end of a ride (pickup or drop-off) as we hand it to a ride app. Plain Java, checked by UberDeepLinkTest.
 *
 * <p>Three kinds:
 * <ul>
 *   <li>{@link #myLocation()}: "wherever the phone is". The ride app finds the position itself, so OnBoard never
 *       needs the location permission for it.</li>
 *   <li>{@link #point}: a place with coordinates, e.g. an airport drop-off point from ride-points.json or the hotel
 *       from the trip. {@code approximate} = the team has not checked that point (the screen says so).</li>
 *   <li>{@link #typed}: an address the traveller typed, with no coordinates.</li>
 * </ul>
 */
public final class RidePlace {

    /** Short name the ride app shows, e.g. "Mumbai Airport Terminal 2, Departures"; null = none. */
    @Nullable
    public final String name;
    /** Full address line; null = none. */
    @Nullable
    public final String address;
    @Nullable
    public final Double lat;
    @Nullable
    public final Double lng;
    public final boolean myLocation;
    /** True for points nobody in the team has checked (ride-points.json "verified": false). */
    public final boolean approximate;

    private RidePlace(@Nullable String name, @Nullable String address, @Nullable Double lat, @Nullable Double lng,
                      boolean myLocation, boolean approximate) {
        this.name = blankToNull(name);
        this.address = blankToNull(address);
        this.lat = lat;
        this.lng = lng;
        this.myLocation = myLocation;
        this.approximate = approximate;
    }

    /** The phone's current position, found by the ride app. */
    public static RidePlace myLocation() {
        return new RidePlace(null, null, null, null, true, false);
    }

    /** A place with coordinates (lat/lng may still be null when the source has none). */
    public static RidePlace point(@Nullable String name, @Nullable String address, @Nullable Double lat,
                                  @Nullable Double lng, boolean approximate) {
        return new RidePlace(name, address, lat, lng, false, approximate);
    }

    /** An address the traveller typed or picked from their saved places: no coordinates. */
    public static RidePlace typed(String address) {
        return new RidePlace(null, address, null, null, false, false);
    }

    /** Both coordinates are known. */
    public boolean hasCoordinates() {
        return lat != null && lng != null;
    }

    /** Nothing a ride app could use: not "my location", no coordinates, no name, no address. */
    public boolean isEmpty() {
        return !myLocation && !hasCoordinates() && name == null && address == null;
    }

    /** What the screen calls this place: the name, else the address (empty for "my location"). */
    public String label() {
        if (name != null) {
            return name;
        }
        return address != null ? address : "";
    }

    @Nullable
    private static String blankToNull(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
