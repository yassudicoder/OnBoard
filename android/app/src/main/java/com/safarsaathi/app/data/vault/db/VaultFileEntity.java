package com.safarsaathi.app.data.vault.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;

/**
 * One encrypted page (image) or PDF of a document. The encrypted bytes are in the file
 * {@code no_backup/vault/files/<id>.bin}; this row holds what is needed to open and check it.
 * (This is the "fileRef + sha256" of the feature brief, as one row per page so multi-page scans work.)
 */
@Entity(tableName = "document_files",
        foreignKeys = @ForeignKey(entity = TravelDocumentEntity.class, parentColumns = "id",
                childColumns = "documentId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("documentId")})
public class VaultFileEntity {

    /** A random UUID; also the file name on the phone. */
    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    public String documentId = "";

    /** 0 = first page. */
    public int page;

    /** "image/jpeg", "image/png" or "application/pdf". */
    @NonNull
    public String mimeType = "";

    /** Size of the original (unencrypted) file in bytes. */
    public long sizeBytes;

    /** SHA-256 of the original file, checked again every time the file is opened or tested. */
    @NonNull
    public String sha256 = "";

    /** This file's own AES key, wrapped (encrypted) with the Keystore master key. See EncryptedFileStore. */
    @NonNull
    public byte[] wrappedKey = new byte[0];

    @NonNull
    public Instant addedAt = Instant.EPOCH;
}
