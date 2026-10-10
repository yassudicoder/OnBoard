package com.safarsaathi.recovery.rules;

/**
 * What kind of change of planes, in the terms airports use for minimum connection times: from a domestic or an
 * international flight to a domestic or an international flight. A flight is domestic when both its airports are in
 * the same country. Saved in the airport rules file by name, e.g. "INTL_INTL".
 */
public enum ConnectionType {
    DOM_DOM,
    DOM_INTL,
    INTL_DOM,
    INTL_INTL;

    /** The type for an inbound and an outbound flight. */
    public static ConnectionType of(boolean inboundInternational, boolean outboundInternational) {
        if (inboundInternational) {
            return outboundInternational ? INTL_INTL : INTL_DOM;
        }
        return outboundInternational ? DOM_INTL : DOM_DOM;
    }
}
