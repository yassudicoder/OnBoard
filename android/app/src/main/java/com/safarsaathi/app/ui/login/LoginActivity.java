package com.safarsaathi.app.ui.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.safarsaathi.app.R;
import com.safarsaathi.app.databinding.ActivityLoginBinding;

/**
 * Demo sign-in, shown once on the first start (CLAUDE.md: "simple demo login, no real auth"). The traveller types a
 * name or continues as a guest; the name is kept on this phone only. No password, no account, no server call.
 * MainActivity opens this screen on top of itself; Back here closes the app (there is nothing to go back to).
 */
public class LoginActivity extends AppCompatActivity {

    private static final String PREFS = "onboard_profile";
    private static final String KEY_SIGNED_IN = "signed_in";
    private static final String KEY_NAME = "name";

    /** True on the first start, and never while instrumented tests run (they start MainActivity directly). */
    public static boolean shouldShow(Context context) {
        return !prefs(context).getBoolean(KEY_SIGNED_IN, false) && !runningInstrumentedTests();
    }

    /** The name typed at sign-in, or empty for a guest. */
    public static String name(Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean runningInstrumentedTests() {
        try {
            Class.forName("androidx.test.platform.app.InstrumentationRegistry");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The navy reaches behind the status and navigation bars (the insets listener below pads the content).
        EdgeToEdge.enable(this);
        ActivityLoginBinding binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Edge-to-edge (targetSdk 37): keep the content clear of the status and navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(binding.loginRoot, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        binding.loginContinue.setOnClickListener(button -> {
            String name = binding.loginName.getText() == null ? "" : binding.loginName.getText().toString().trim();
            if (name.isEmpty()) {
                binding.loginNameLayout.setError(getString(R.string.login_name_error));
                return;
            }
            signIn(name);
        });
        binding.loginName.setOnEditorActionListener((field, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                binding.loginContinue.performClick();
                return true;
            }
            return false;
        });
        binding.loginGuest.setOnClickListener(button -> signIn(""));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });
    }

    private void signIn(String name) {
        prefs(this).edit().putBoolean(KEY_SIGNED_IN, true).putString(KEY_NAME, name).apply();
        finish();
    }
}
