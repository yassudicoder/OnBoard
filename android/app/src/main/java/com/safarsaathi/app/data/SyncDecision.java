package com.safarsaathi.app.data;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.local.TripEntity;

/**
 * THE SYNC RULE: when a fresh copy of a trip arrives from the server, should it replace the copy saved
 * on the phone? Plain Java, checked by SyncDecisionTest.
 *
 * <p>Replace when:
 * <ol>
 *   <li>there is no saved copy yet, or</li>
 *   <li>the server's version is higher (the plan changed, e.g. after a flight delay), or</li>
 *   <li>the server's createdAt differs: the trip was created again, e.g. after POST /api/demo/reset.
 *       The reloaded trip starts again at version 1, so comparing versions alone would wrongly keep the
 *       old (delayed) plan on the phone.</li>
 * </ol>
 * Otherwise the saved copy stays as it is (only its "last synced" time is updated). Room stays the
 * screen's source of truth, and an older or equal copy never overwrites a newer one (CLAUDE.md).
 */
public final class SyncDecision {

    private SyncDecision() {
    }

    /**
     * @param saved      the copy on the phone, or null when there is none
     * @param fromServer the copy that just arrived
     */
    public static boolean shouldReplace(@Nullable TripEntity saved, TripEntity fromServer) {
        if (saved == null) {
            return true;
        }
        if (fromServer.version > saved.version) {
            return true;
        }
        return !fromServer.createdAt.equals(saved.createdAt);
    }
}
