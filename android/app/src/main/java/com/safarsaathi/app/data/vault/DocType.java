package com.safarsaathi.app.data.vault;

/**
 * The kinds of travel document the vault knows. The order here is the order a trip folder lists them in
 * ("by type"): identity first, then permission to enter, then travel, stay and cover.
 * Stored in the database by name (e.g. "E_VISA"), so never rename a value; add new ones instead.
 */
public enum DocType {
    PASSPORT,
    ID_CARD,
    VISA,
    E_VISA,
    TRANSIT_VISA,
    ENTRY_FORM,
    TICKET,
    BOARDING_PASS,
    HOTEL,
    INSURANCE,
    VACCINATION_CERT,
    OTHER
}
