package com.safarsaathi.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * What the last "refresh from the server" did, for the sync line under the trip title:
 * "Refreshing…", "Updated 17:42" or "Offline · showing your saved plan from 17:42".
 * Immutable: a new object is published for every change (TripRepository.getSyncState()).
 */
public final class SyncState {

    public enum Kind {
        /** Nothing has been tried yet. */
        IDLE,
        /** A request to the server is running. */
        REFRESHING,
        /** The server answered; the phone has its latest plan. */
        UP_TO_DATE,
        /** The server could not be reached or sent an error; the screen shows the saved plan. */
        OFFLINE
    }

    /** Why a refresh failed. The screen picks a plain-language message for each one. */
    public enum Problem {
        NONE,
        /** No internet, server not running, or it took too long. */
        NO_CONNECTION,
        /** The server answered 404: it has no trip with this id. */
        NOT_FOUND,
        /** The server answered with another error (e.g. 500). */
        SERVER_ERROR,
        /** The answer could not be read or saved. */
        BAD_DATA
    }

    public final Kind kind;
    public final Problem problem;
    /** Technical detail for Logcat (e.g. "HTTP 500"); never shown to the traveller as it is. */
    @Nullable
    public final String detail;
    /** When the phone last heard from the server (null = never). */
    @Nullable
    public final Instant lastSyncedAt;

    private SyncState(Kind kind, Problem problem, @Nullable String detail, @Nullable Instant lastSyncedAt) {
        this.kind = kind;
        this.problem = problem;
        this.detail = detail;
        this.lastSyncedAt = lastSyncedAt;
    }

    public static SyncState idle() {
        return new SyncState(Kind.IDLE, Problem.NONE, null, null);
    }

    public static SyncState refreshing() {
        return new SyncState(Kind.REFRESHING, Problem.NONE, null, null);
    }

    public static SyncState upToDate(Instant syncedAt) {
        return new SyncState(Kind.UP_TO_DATE, Problem.NONE, null, syncedAt);
    }

    public static SyncState offline(Problem problem, @Nullable String detail, @Nullable Instant lastSyncedAt) {
        return new SyncState(Kind.OFFLINE, problem, detail, lastSyncedAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SyncState)) {
            return false;
        }
        SyncState that = (SyncState) other;
        return kind == that.kind
                && problem == that.problem
                && Objects.equals(detail, that.detail)
                && Objects.equals(lastSyncedAt, that.lastSyncedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, problem, detail, lastSyncedAt);
    }

    @NonNull
    @Override
    public String toString() {
        return kind + (problem == Problem.NONE ? "" : " " + problem) + (detail == null ? "" : " (" + detail + ")");
    }
}
