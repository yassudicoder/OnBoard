/**
 * The Ripple engine ({@code RippleEngine}): when one block moves or is cancelled (e.g. a flight delay), it walks the
 * links and marks every later block OK, AUTO_ADJUSTED (moved automatically) or NEEDS_USER (the traveller decides),
 * and lists the links whose timing no longer works.
 *
 * <p>Built and unit-tested ({@code RippleEngineTest}). Pure Java and deterministic: it takes small records of its own
 * ({@code RippleBlock}, {@code RippleLink}, {@code RippleChange}) and returns a {@code RippleResult}, so it has no
 * link to Spring or the database. From the trip package it uses only the two plain enums {@code Flex} and
 * {@code BlockStatus}, so there is one set of values everywhere.
 *
 * <p>{@code watcher.DisruptionService} is the caller: it copies the trip's blocks and links in, runs the engine and
 * saves the result in one database transaction.
 */
package com.safarsaathi.backend.ripple;
