package com.safarsaathi.app.data.vault;

import com.safarsaathi.app.data.vault.crypto.EncryptedFileStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of decrypt-testing vault files: how many were checked, their total size, and which ones failed and why.
 * V1 shows it on the vault settings screen; V6 uses it for "make offline-ready".
 */
public final class FileCheckReport {

    /** One file that did not pass. */
    public static final class Problem {
        public final String fileId;
        public final String documentId;
        public final EncryptedFileStore.Check result;

        public Problem(String fileId, String documentId, EncryptedFileStore.Check result) {
            this.fileId = fileId;
            this.documentId = documentId;
            this.result = result;
        }
    }

    private int checked;
    private long totalBytes;
    private final List<Problem> problems = new ArrayList<>();

    /** Adds one file's result. {@code sizeBytes} = size of the original file. */
    public void add(String fileId, String documentId, long sizeBytes, EncryptedFileStore.Check result) {
        checked++;
        totalBytes += sizeBytes;
        if (result != EncryptedFileStore.Check.OK) {
            problems.add(new Problem(fileId, documentId, result));
        }
    }

    public int checked() {
        return checked;
    }

    public int passed() {
        return checked - problems.size();
    }

    public long totalBytes() {
        return totalBytes;
    }

    public boolean allOk() {
        return problems.isEmpty();
    }

    public List<Problem> problems() {
        return Collections.unmodifiableList(problems);
    }
}
