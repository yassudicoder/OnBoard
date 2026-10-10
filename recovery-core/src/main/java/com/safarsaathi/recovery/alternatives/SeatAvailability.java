package com.safarsaathi.recovery.alternatives;

/** Are there seats on an alternative? Only a provider that checked can say VERIFIED; everything else is UNKNOWN. */
public enum SeatAvailability {
    VERIFIED,
    /** Shown as "availability unknown". The SAMPLE schedule never knows. */
    UNKNOWN
}
