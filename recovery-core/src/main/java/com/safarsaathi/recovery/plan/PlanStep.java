package com.safarsaathi.recovery.plan;

/** One numbered next step of a recovery plan, e.g. "2. Ask the airline to rebook you or refund you ...". */
public final class PlanStep {

    public int number;
    /** CHECK_REBOOKING, ASK_AIRLINE, COMPARE, TRANSIT, OVERNIGHT or CONFIRM: lets the app add the right button. */
    public String kind;
    public String text;

    /** For the JSON readers. */
    public PlanStep() {
    }

    public PlanStep(int number, String kind, String text) {
        this.number = number;
        this.kind = kind;
        this.text = text;
    }
}
