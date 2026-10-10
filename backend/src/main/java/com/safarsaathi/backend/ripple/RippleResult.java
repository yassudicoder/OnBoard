package com.safarsaathi.backend.ripple;

import java.util.List;
import java.util.Optional;

/**
 * What the ripple engine found.
 *
 * @param changedBlockId the block that changed
 * @param outcomes       the changed block first, then every block that waits for it (directly or through other
 *                       blocks), in trip order. Blocks that do not depend on the change are not listed: they
 *                       keep their status and times
 * @param brokenLinks    links whose timing no longer works, in trip order
 */
public record RippleResult(long changedBlockId, List<BlockOutcome> outcomes, List<BrokenLink> brokenLinks) {

    public RippleResult {
        // Read-only copies: a result never changes after it is made.
        outcomes = List.copyOf(outcomes);
        brokenLinks = List.copyOf(brokenLinks);
    }

    /** The outcome for one block, or empty when the change does not affect that block. */
    public Optional<BlockOutcome> outcomeFor(long blockId) {
        return outcomes.stream()
                .filter(outcome -> outcome.blockId() == blockId)
                .findFirst();
    }
}
