package com.safarsaathi.recovery.plan;

/** What broke the journey. All three lead to the same kind of recovery plan; only where it starts differs. */
public enum RecoveryTrigger {
    /** Flight 1 is late, so the connection is at risk or missed. The plan starts at the hub. */
    DELAY_MISSED_CONNECTION,
    /** Flight 1 is cancelled before it leaves. The plan starts at the departure airport. */
    CANCELLED_BEFORE_DEPARTURE,
    /** Flight 2 is cancelled; the traveller is at (or on the way to) the hub. The plan starts at the hub. */
    CONNECTION_CANCELLED_AT_HUB
}
