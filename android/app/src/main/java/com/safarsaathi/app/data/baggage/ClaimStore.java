package com.safarsaathi.app.data.baggage;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.File;

/**
 * Keeps the bag-problem drafts on this phone: one draft per issue (damaged / didn't arrive / item missing) as JSON in
 * its own SharedPreferences file, and the photos as files in no_backup/bag-evidence/. Both are left out of cloud
 * backup (res/xml/data_extraction_rules.xml excludes this preferences file; no_backup is never backed up). Nothing is
 * uploaded: the server copy and signed receipt come with Baggage Shield B1/B2.
 */
public final class ClaimStore {

    private static final String PREFS = "bag_claims";
    private static final String PHOTO_DIR = "bag-evidence";

    private final SharedPreferences prefs;
    private final File photoDir;
    private final Gson gson = new Gson();

    public ClaimStore(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        photoDir = new File(app.getNoBackupFilesDir(), PHOTO_DIR);
    }

    public ClaimDraft load(BagIssue issue) {
        String json = prefs.getString(issue.name(), null);
        ClaimDraft draft = null;
        if (json != null) {
            try {
                draft = gson.fromJson(json, ClaimDraft.class);
            } catch (JsonParseException e) {
                draft = null; // a broken draft starts again empty rather than crashing the Help tab
            }
        }
        if (draft == null) {
            draft = new ClaimDraft();
        }
        draft.issue = issue.name();
        return draft;
    }

    public void save(ClaimDraft draft) {
        prefs.edit().putString(draft.issue().name(), gson.toJson(draft)).apply();
    }

    /** The folder for the photo files (created when missing). */
    public File photoDir() {
        if (!photoDir.isDirectory() && !photoDir.mkdirs()) {
            throw new IllegalStateException("Cannot create " + photoDir);
        }
        return photoDir;
    }

    public File photoFile(EvidencePhoto photo) {
        return new File(photoDir(), photo.fileName);
    }

    /** Removes the draft's photos from the phone and empties the draft (the traveller's "Start again"). */
    public void clear(BagIssue issue) {
        ClaimDraft draft = load(issue);
        for (EvidencePhoto photo : draft.photos) {
            File file = photoFile(photo);
            if (file.exists() && !file.delete()) {
                file.deleteOnExit();
            }
        }
        prefs.edit().remove(issue.name()).apply();
    }
}
