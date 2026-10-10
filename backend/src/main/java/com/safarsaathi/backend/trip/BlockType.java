package com.safarsaathi.backend.trip;

/** What kind of booking or event a block is. Saved by name, e.g. "FLIGHT". */
public enum BlockType {
    FLIGHT,
    /** Waiting for checked bags at the arrival airport. */
    BAG_CLAIM,
    CAB,
    HOTEL,
    MEETING,
    /** e.g. a wedding ceremony or a concert. */
    EVENT,
    RESTAURANT,
    TRAIN,
    OTHER
}
