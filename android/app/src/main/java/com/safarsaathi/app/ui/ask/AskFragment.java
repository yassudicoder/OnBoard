package com.safarsaathi.app.ui.ask;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.safarsaathi.app.BuildConfig;
import com.safarsaathi.app.MainActivity;
import com.safarsaathi.app.R;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.ask.Answer;
import com.safarsaathi.app.data.ask.AnswerText;
import com.safarsaathi.app.data.ask.AskLanguage;
import com.safarsaathi.app.data.ask.DemoTripOnPhone;
import com.safarsaathi.app.databinding.FragmentAskBinding;

import java.time.Instant;
import java.util.Collections;

/**
 * Ask OnBoard (layout: fragment_ask.xml): the traveller types or says a question in English, Hindi, Marathi or
 * Hinglish and gets the answer in the same language, from the data saved on the phone (Layer 1, Quick answers; see
 * {@link AskViewModel} and data/ask). Works in airplane mode.
 *
 * <p>Voice: the round button is a microphone while the text field is empty (Android's speech recognizer, offline when
 * the phone has the language pack, see {@link SpeechInput}) and "send" once there is text. Answers to a spoken
 * question are read aloud ({@link AnswerVoice}); any answer can be replayed with its speaker button.
 *
 * <p>Opened from the "Ask OnBoard" bar on Home; argument {@code startVoice} = start listening at once (Home's mic).
 * The bottom tabs are hidden here, so the chat and the keyboard get the whole screen.
 */
public class AskFragment extends Fragment {

    /** Navigation argument: start listening as soon as the screen opens. */
    public static final String ARG_START_VOICE = "startVoice";

    /** The quick questions, most asked first (the brief's three, then the others). */
    /** Debug menu item id (a plain number: menu items added in code need no resource id). */
    private static final int MENU_DEMO_TRIP = 1;

    private static final String[] CHIP_KEYS = {"chip.gate", "chip.boarding_close", "chip.bag", "chip.washroom",
            "chip.flight", "chip.next"};

    // Only valid between onViewCreated and onDestroyView.
    private FragmentAskBinding binding;
    private AskViewModel viewModel;
    private ChatAdapter adapter;
    private SpeechInput speech;
    private AnswerVoice voice;
    /** The window's keyboard behaviour before this screen changed it (restored in onPause). */
    private int previousSoftInputMode;

    /** Android's "allow OnBoard to record audio?" question. Must be registered before the screen starts. */
    private final ActivityResultLauncher<String> micPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startListening();
                } else if (binding != null) {
                    snack(getString(R.string.ask_voice_no_permission));
                }
            });

    // Android re-creates fragments by calling this no-argument constructor, so it must stay public.
    public AskFragment() {
        super(R.layout.fragment_ask);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentAskBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(AskViewModel.class);
        applyInsets();

        adapter = new ChatAdapter(new ChatAdapter.Callbacks() {
            @Override
            public void onAction(Answer.Action action) {
                openScreen(action);
            }

            @Override
            public void onSpeak(Answer answer) {
                voice.speak(answer.text, answer.language);
            }
        });
        LinearLayoutManager layout = new LinearLayoutManager(requireContext());
        layout.setStackFromEnd(true); // new messages at the bottom, like any chat
        binding.askMessages.setLayoutManager(layout);
        binding.askMessages.setAdapter(adapter);

        speech = new SpeechInput(requireContext(), new SpeechListener());
        voice = new AnswerVoice(requireContext(), language -> {
            if (binding != null) {
                snack(getString(R.string.ask_voice_missing, getString(languageName(language))));
            }
        });

        binding.askLanguageChip.setOnClickListener(chip -> chooseLanguage());
        binding.askAction.setOnClickListener(button -> onActionButton());
        binding.askInput.setOnEditorActionListener((field, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTyped();
                return true;
            }
            return false;
        });
        binding.askInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateActionButton();
            }
        });

        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            adapter.submitList(messages, () -> {
                if (binding != null && !messages.isEmpty()) {
                    binding.askMessages.scrollToPosition(messages.size() - 1);
                }
            });
            binding.askWelcome.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getChosenLanguage().observe(getViewLifecycleOwner(), chosen ->
                binding.askLanguageChip.setText(chosen == null
                        ? getString(R.string.ask_language_chip_auto)
                        : getString(R.string.ask_language_chip_fixed, getString(shortName(chosen)))));
        viewModel.getConversationLanguage().observe(getViewLifecycleOwner(), this::showQuickQuestions);
        viewModel.getToSpeak().observe(getViewLifecycleOwner(), answer -> {
            if (answer != null) {
                voice.speak(answer.text, answer.language);
                viewModel.spoken();
            }
        });
        updateActionButton();
        addDebugMenu();

        boolean startVoice = getArguments() != null && getArguments().getBoolean(ARG_START_VOICE, false);
        if (savedInstanceState == null && startVoice) {
            onMicTapped();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setTabsVisible(false);
        }
        // The app draws edge to edge, so the keyboard does not push the screen up by itself: with "adjust resize" the
        // window reports the keyboard's height (insets) and applyInsets() lifts the input bar above it. Only while this
        // screen shows, so other screens keep their own behaviour.
        previousSoftInputMode = requireActivity().getWindow().getAttributes().softInputMode;
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override
    public void onPause() {
        requireActivity().getWindow().setSoftInputMode(previousSoftInputMode);
        voice.silence();
        speech.destroy();
        showListening(null);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        speech.destroy();
        voice.shutdown();
        binding = null;
        super.onDestroyView();
    }

    // ---- Asking ----

    private void onActionButton() {
        if (!text().isEmpty()) {
            sendTyped();
        } else if (speech.isListening()) {
            speech.stop();
        } else {
            onMicTapped();
        }
    }

    private void sendTyped() {
        String question = text();
        if (question.isEmpty()) {
            return;
        }
        voice.silence();
        viewModel.ask(question, false);
        binding.askInput.setText("");
    }

    private void onMicTapped() {
        if (!SpeechInput.isAvailable(requireContext())) {
            snack(getString(R.string.ask_voice_unavailable));
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.ask_mic_rationale)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, which) -> micPermission.launch(Manifest.permission.RECORD_AUDIO))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startListening() {
        if (binding == null) {
            return;
        }
        voice.silence();
        AskLanguage language = listeningLanguage();
        showListening(language);
        speech.start(language.speechTag);
        updateActionButton();
    }

    /** The traveller's chosen language, else the language of the conversation so far (or the app's language). */
    private AskLanguage listeningLanguage() {
        AskLanguage chosen = viewModel.getChosenLanguage().getValue();
        if (chosen != null) {
            return chosen;
        }
        AskLanguage current = viewModel.getConversationLanguage().getValue();
        return current != null ? current : AskLanguage.EN;
    }

    private final class SpeechListener implements SpeechInput.Listener {
        @Override
        public void onListening() {
            showListening(listeningLanguage());
        }

        @Override
        public void onPartial(String text) {
            if (binding != null) {
                binding.askInput.setText(text);
            }
        }

        @Override
        public void onHeard(String text) {
            if (binding != null) {
                binding.askInput.setText("");
            }
            viewModel.ask(text, true);
        }

        @Override
        public void onProblem(SpeechInput.Problem problem) {
            if (binding == null) {
                return;
            }
            binding.askInput.setText("");
            String message;
            switch (problem) {
                case NEEDS_PACK_OR_INTERNET:
                    message = getString(R.string.ask_voice_needs_pack, getString(languageName(listeningLanguage())));
                    break;
                case NO_PERMISSION:
                    message = getString(R.string.ask_voice_no_permission);
                    break;
                case MICROPHONE:
                    message = getString(R.string.ask_voice_mic_problem);
                    break;
                case BUSY:
                    message = getString(R.string.ask_voice_busy);
                    break;
                case NOT_HEARD:
                default:
                    message = getString(R.string.ask_voice_not_heard);
                    break;
            }
            snack(message);
        }

        @Override
        public void onStopped() {
            showListening(null);
            updateActionButton();
        }
    }

    // ---- Screen pieces ----

    /** "Listening in हिंदी…" while the microphone is open; hidden otherwise (null). */
    private void showListening(@Nullable AskLanguage language) {
        if (binding == null) {
            return;
        }
        binding.askListening.setVisibility(language == null ? View.GONE : View.VISIBLE);
        if (language != null) {
            binding.askListening.setText(getString(R.string.ask_listening, getString(languageName(language))));
        }
    }

    /** The round button: send when there is text, else stop (while listening) or the microphone. */
    private void updateActionButton() {
        if (binding == null) {
            return;
        }
        boolean hasText = !text().isEmpty() && !speech.isListening();
        boolean listening = speech.isListening();
        binding.askAction.setIconResource(hasText ? R.drawable.ic_send_24
                : listening ? R.drawable.ic_close_24 : R.drawable.ic_mic_24);
        binding.askAction.setContentDescription(getString(hasText ? R.string.ask_send
                : listening ? R.string.ask_mic_stop : R.string.ask_mic));
    }

    /** The quick-question chips, in the conversation's language: tapping one asks it. */
    private void showQuickQuestions(@Nullable AskLanguage language) {
        AnswerText text = AnswerText.of(language == null ? AskLanguage.EN : language);
        binding.askChips.removeAllViews();
        for (String key : CHIP_KEYS) {
            Chip chip = new Chip(requireContext());
            String question = text.get(key);
            chip.setText(question);
            chip.setOnClickListener(tapped -> {
                voice.silence();
                viewModel.ask(question, false);
            });
            binding.askChips.addView(chip);
        }
    }

    /** The language chip: Auto, or one fixed reply language (remembered on the phone). */
    private void chooseLanguage() {
        AskLanguage[] languages = AskLanguage.values();
        String[] names = new String[languages.length + 1];
        names[0] = getString(R.string.ask_language_auto_detail);
        for (int i = 0; i < languages.length; i++) {
            names[i + 1] = getString(languageName(languages[i]));
        }
        AskLanguage chosen = viewModel.getChosenLanguage().getValue();
        int selected = chosen == null ? 0 : chosen.ordinal() + 1;
        new MaterialAlertDialogBuilder(requireContext())
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    viewModel.chooseLanguage(which == 0 ? null : languages[which - 1]);
                    dialog.dismiss();
                })
                .show();
    }

    /** Opens the screen an answer's button points to, exactly as if the traveller had tapped that tab. */
    private void openScreen(Answer.Action action) {
        if (action == Answer.Action.OPEN_LOST_FOUND) {
            // Not a tab: the Lost & Found guide from the Help tab (B3), opened on top of this screen.
            NavHostFragment.findNavController(this).navigate(R.id.navigation_lost_found);
            return;
        }
        int tab;
        if (action == Answer.Action.OPEN_HELP) {
            tab = R.id.navigation_help;
        } else if (action == Answer.Action.OPEN_VAULT || action == Answer.Action.UNLOCK_VAULT) {
            // The Document Vault is not on this branch yet (CLAUDE.md §3E): Profile is where its link will be.
            tab = R.id.navigation_profile;
        } else if (action == Answer.Action.OPEN_BAGS) {
            tab = R.id.navigation_bags;
        } else if (action == Answer.Action.OPEN_HOME) {
            tab = R.id.navigation_home;
        } else {
            tab = R.id.navigation_navigate; // NAVIGATE and OPEN_MAP
        }
        BottomNavigationView tabs = requireActivity().findViewById(R.id.bottom_nav);
        if (tabs != null) {
            tabs.setSelectedItemId(tab);
        }
    }

    /**
     * Edge to edge: the tabs are hidden here, so keep the input bar above the navigation bar, and above the keyboard
     * while it is open (whichever is taller). Left and right are handled by MainActivity.
     */
    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.askRoot, (root, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(),
                    Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
    }

    // ---- Debug builds: load the demo trip on the phone ----

    /**
     * Debug builds only: "Load demo trip on phone" in the toolbar menu, so trip answers can be shown on an emulator or a
     * phone without the backend (CLAUDE.md §3E). Release builds never show it.
     */
    private void addDebugMenu() {
        if (!BuildConfig.DEBUG) {
            return;
        }
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                menu.add(Menu.NONE, MENU_DEMO_TRIP, Menu.NONE, R.string.ask_demo_trip_menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() != MENU_DEMO_TRIP) {
                    return false;
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.ask_demo_trip_menu)
                        .setMessage(R.string.ask_demo_trip_confirm)
                        .setPositiveButton(R.string.ask_demo_trip_load, (dialog, which) -> loadDemoTrip())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    /** Saves the demo trip like a server copy (on the database thread); Home and Ask show it at once via Room. */
    private void loadDemoTrip() {
        DemoTripOnPhone demo = DemoTripOnPhone.build(Instant.now());
        ServiceLocator.get().getIoExecutor().execute(() -> ServiceLocator.get().getDatabase().tripDao()
                .replaceTrip(demo.trip, demo.blocks, Collections.emptyList()));
        snack(getString(R.string.ask_demo_trip_loaded));
    }

    /** A short message just above the input bar (not over it). */
    private void snack(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).setAnchorView(binding.askInputBar).show();
        }
    }

    private String text() {
        Editable editable = binding.askInput.getText();
        return editable == null ? "" : editable.toString().trim();
    }

    @StringRes
    static int languageName(AskLanguage language) {
        switch (language) {
            case HI:
                return R.string.ask_language_hi;
            case MR:
                return R.string.ask_language_mr;
            case HINGLISH:
                return R.string.ask_language_hinglish;
            case EN:
            default:
                return R.string.ask_language_en;
        }
    }

    @StringRes
    private static int shortName(AskLanguage language) {
        switch (language) {
            case HI:
                return R.string.ask_language_short_hi;
            case MR:
                return R.string.ask_language_short_mr;
            case HINGLISH:
                return R.string.ask_language_short_hinglish;
            case EN:
            default:
                return R.string.ask_language_short_en;
        }
    }
}
