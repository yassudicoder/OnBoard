package com.safarsaathi.recovery.rules;

/**
 * One minimum connection time (MCT) of an airport: "an international to international connection in Doha needs at
 * least 60 minutes". An MCT is the shortest time between landing and the next departure that the airport or airline
 * accepts for changing planes. Below it, the airline itself would not sell the connection.
 */
public final class ConnectionRule {

    public ConnectionType type;
    /**
     * true = only when you change terminals, false = only when you stay in one terminal, null = either way.
     * An airport with one terminal (or one figure for all) leaves it null.
     */
    public Boolean terminalChange;
    public int minutes;
    public ValueKind kind;
    /** Where the figure comes from, in words. A DEMO_VALUE always says "DEMO VALUE - not an official figure". */
    public String source;
    /** The official page; null for a demo value. */
    public String sourceUrl;
    /** When a teammate last checked the source, as yyyy-MM-dd. */
    public String lastChecked;

    /** For the JSON readers. */
    public ConnectionRule() {
    }

    public ConnectionRule(ConnectionType type, Boolean terminalChange, int minutes, ValueKind kind, String source,
                          String sourceUrl, String lastChecked) {
        this.type = type;
        this.terminalChange = terminalChange;
        this.minutes = minutes;
        this.kind = kind;
        this.source = source;
        this.sourceUrl = sourceUrl;
        this.lastChecked = lastChecked;
    }

    /**
     * Does this rule apply? {@code terminalChange} null = we don't know the terminals, so a rule for either case
     * may apply (the checker then picks the strictest one).
     */
    public boolean appliesTo(ConnectionType connectionType, Boolean changesTerminal) {
        if (type != connectionType) {
            return false;
        }
        return terminalChange == null || changesTerminal == null || terminalChange.equals(changesTerminal);
    }
}
