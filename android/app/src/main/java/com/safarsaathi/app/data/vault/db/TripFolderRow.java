package com.safarsaathi.app.data.vault.db;

import androidx.annotation.NonNull;
import androidx.room.Embedded;

/** A trip folder for the vault's home list: the trip plus how many documents it holds. */
public class TripFolderRow {

    @Embedded
    @NonNull
    public TripVaultEntity trip = new TripVaultEntity();

    public int documentCount;
}
