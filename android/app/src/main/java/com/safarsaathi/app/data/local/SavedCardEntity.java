package com.safarsaathi.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.Instant;
import java.util.Objects;

/**
 * A bank card the traveller holds, so card offers can be applied to prices later (true-cost compare, Phase 7).
 * Room table "saved_cards"; phone-only. Mirrors CLAUDE.md's UserCard.
 *
 * <p>ONLY the bank, the card type and the last 4 digits: never the full number, expiry or CVV.
 */
@Entity(tableName = "saved_cards")
public class SavedCardEntity {

    public static final String TYPE_CREDIT = "CREDIT";
    public static final String TYPE_DEBIT = "DEBIT";

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** e.g. "HDFC Bank" */
    @NonNull
    public String bank = "";

    /** CREDIT or DEBIT. */
    @NonNull
    public String cardType = TYPE_CREDIT;

    /** Exactly 4 digits. */
    @NonNull
    public String last4 = "";

    @NonNull
    public Instant addedAt = Instant.EPOCH;

    /** True for exactly four digits 0-9, the only part of a card number the app ever keeps. */
    public static boolean isValidLast4(String text) {
        return text != null && text.length() == 4 && text.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SavedCardEntity)) {
            return false;
        }
        SavedCardEntity that = (SavedCardEntity) other;
        return id == that.id && bank.equals(that.bank) && cardType.equals(that.cardType) && last4.equals(that.last4)
                && addedAt.equals(that.addedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, bank, cardType, last4, addedAt);
    }
}
