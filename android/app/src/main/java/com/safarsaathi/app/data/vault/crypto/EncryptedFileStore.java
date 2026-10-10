package com.safarsaathi.app.data.vault.crypto;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;

/**
 * Where the vault keeps document files (scans, photos, PDFs): one encrypted file per page, in the app's private
 * folder. Plain Java (java.io), so it is tested on the laptop with a temporary folder.
 *
 * <p>How one file is protected ("envelope encryption", like a letter in a locked box whose key sits in a safe):
 * <ol>
 *   <li>a brand-new random AES-256 key is made for this one file (the "file key");</li>
 *   <li>the file is encrypted with that key ({@link AesGcm}, label = the file's id) and written as {@code <id>.bin};</li>
 *   <li>the file key is itself encrypted ("wrapped") with the vault's {@link VaultMasterKey}, which lives in the
 *       Android Keystore. The wrapped key is returned to the caller, who saves it in the (also encrypted) vault
 *       database together with the file's SHA-256 fingerprint.</li>
 * </ol>
 * Why not encrypt the files with the Keystore key directly? The Keystore's secure hardware is built for small
 * secrets and is slow for megabytes. This way the hardware only ever handles 32-byte keys and the phone's fast
 * normal AES does the big files. Bonus: deleting a file's wrapped key makes that file unreadable for good.
 */
public final class EncryptedFileStore {

    /**
     * Largest single page or PDF the vault accepts. The whole file is encrypted and decrypted in memory, so this
     * keeps memory use safe on older phones; scans and e-tickets are far smaller.
     */
    public static final int MAX_FILE_BYTES = 25 * 1024 * 1024;

    /** File ids are UUIDs, e.g. "3f2a0c1e-...". Anything else is refused, so an id can never point outside the folder. */
    private static final Pattern FILE_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** What a stored file's database row needs to remember. */
    public static final class StoredFile {
        public final String fileId;
        /** The file key, encrypted with the master key. Save it; without it the file can never be read. */
        public final byte[] wrappedKey;
        /** SHA-256 of the ORIGINAL (unencrypted) content. */
        public final String sha256;
        /** Size of the original content in bytes. */
        public final long sizeBytes;

        StoredFile(String fileId, byte[] wrappedKey, String sha256, long sizeBytes) {
            this.fileId = fileId;
            this.wrappedKey = wrappedKey;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
        }
    }

    /** Result of checking one file without showing it. */
    public enum Check {
        /** Decrypted fine and the SHA-256 fingerprint matches what was saved. */
        OK,
        /** The encrypted file is not on the phone any more. */
        MISSING,
        /** It could not be decrypted: changed on disk, or its key is gone (e.g. the master key was deleted). */
        UNREADABLE,
        /** It decrypted, but the content is not what was saved (fingerprint differs). */
        CHANGED
    }

    /** Thrown by {@link #read} when a file decrypts but its fingerprint does not match. */
    public static final class IntegrityException extends GeneralSecurityException {
        IntegrityException(String message) {
            super(message);
        }
    }

    private final File folder;
    private final VaultMasterKey masterKey;

    public EncryptedFileStore(File folder, VaultMasterKey masterKey) {
        this.folder = folder;
        this.masterKey = masterKey;
    }

    /** Encrypts {@code content} and saves it under {@code fileId}. */
    public StoredFile write(String fileId, byte[] content) throws IOException, GeneralSecurityException {
        checkId(fileId);
        if (content.length > MAX_FILE_BYTES) {
            throw new IOException("File is larger than " + (MAX_FILE_BYTES / (1024 * 1024)) + " MB");
        }
        SecretKey fileKey = AesGcm.newKey();
        byte[] box = AesGcm.encrypt(fileKey, content, fileLabel(fileId));
        byte[] wrappedKey = AesGcm.encrypt(masterKey.get(), fileKey.getEncoded(), keyLabel(fileId));

        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Could not create the vault folder");
        }
        // Write to a temporary name first and rename at the end, so a crash halfway never leaves half a file
        // under the real name.
        File temporary = new File(folder, fileId + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(box);
            out.getFD().sync(); // make sure it is really on the storage before we rename it
        }
        File target = fileFor(fileId);
        if (!temporary.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw new IOException("Could not save the encrypted file");
        }
        return new StoredFile(fileId, wrappedKey, Sha256.hex(content), content.length);
    }

    /**
     * Decrypts a file and checks its fingerprint.
     *
     * @throws java.io.FileNotFoundException when the file is gone
     * @throws javax.crypto.AEADBadTagException when it was changed on disk or the key is wrong
     * @throws IntegrityException when it decrypts but is not the content that was saved
     */
    public byte[] read(String fileId, byte[] wrappedKey, String expectedSha256)
            throws IOException, GeneralSecurityException {
        checkId(fileId);
        byte[] box = Files.readAllBytes(fileFor(fileId).toPath());
        byte[] keyBytes = AesGcm.decrypt(masterKey.get(), wrappedKey, keyLabel(fileId));
        byte[] content = AesGcm.decrypt(AesGcm.keyFromBytes(keyBytes), box, fileLabel(fileId));
        if (!Sha256.same(expectedSha256, Sha256.hex(content))) {
            throw new IntegrityException("File " + fileId + " does not match its saved fingerprint");
        }
        return content;
    }

    /** The "decrypt-test": opens the file in memory, compares the fingerprint, and forgets the content again. */
    public Check check(String fileId, byte[] wrappedKey, String expectedSha256) {
        if (!fileFor(fileId).isFile()) {
            return Check.MISSING;
        }
        try {
            read(fileId, wrappedKey, expectedSha256);
            return Check.OK;
        } catch (IntegrityException e) {
            return Check.CHANGED;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            return Check.UNREADABLE;
        }
    }

    /** Deletes the encrypted file (its database row, with the wrapped key, is deleted by the caller). */
    public void delete(String fileId) {
        checkId(fileId);
        //noinspection ResultOfMethodCallIgnored
        fileFor(fileId).delete();
    }

    /** Size of the encrypted file on the phone, 0 when it is missing. */
    public long sizeOnDisk(String fileId) {
        checkId(fileId);
        return fileFor(fileId).length();
    }

    /** The encrypted file itself. Only for tests and the vault's own housekeeping. */
    public File fileFor(String fileId) {
        checkId(fileId);
        return new File(folder, fileId + ".bin");
    }

    private static void checkId(String fileId) {
        if (fileId == null || !FILE_ID.matcher(fileId).matches()) {
            throw new IllegalArgumentException("Not a vault file id: " + fileId);
        }
    }

    // Two different labels, so a wrapped key can never be mistaken for a file, or the other way round.
    private static String fileLabel(String fileId) {
        return "file:" + fileId;
    }

    private static String keyLabel(String fileId) {
        return "file-key:" + fileId;
    }
}
