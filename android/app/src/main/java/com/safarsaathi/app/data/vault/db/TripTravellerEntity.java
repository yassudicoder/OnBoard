package com.safarsaathi.app.data.vault.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/** Who travels on a trip (a trip can have several travellers, a traveller several trips). */
@Entity(tableName = "trip_travellers",
        primaryKeys = {"tripId", "travellerId"},
        foreignKeys = {
                @ForeignKey(entity = TripVaultEntity.class, parentColumns = "id", childColumns = "tripId",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = TravellerEntity.class, parentColumns = "id", childColumns = "travellerId",
                        onDelete = ForeignKey.CASCADE)},
        indices = {@Index("travellerId")})
public class TripTravellerEntity {

    @NonNull
    public String tripId = "";

    @NonNull
    public String travellerId = "";
}
