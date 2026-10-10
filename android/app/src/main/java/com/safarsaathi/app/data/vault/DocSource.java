package com.safarsaathi.app.data.vault;

/**
 * How a document got into the vault: camera SCAN, a PDF file, typed in by hand (MANUAL), or another IMPORT
 * (e.g. an image from the gallery). Demo documents are marked separately ({@code sample = true}).
 */
public enum DocSource {
    SCAN,
    PDF,
    MANUAL,
    IMPORT
}
