package com.safarsaathi.app.ui.ask;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.safarsaathi.app.AppConfig;
import com.safarsaathi.app.ServiceLocator;
import com.safarsaathi.app.data.TripRepository;
import com.safarsaathi.app.data.airport.AirportMapRepository;
import com.safarsaathi.app.data.airport.MapJson;
import com.safarsaathi.app.data.ask.Answer;
import com.safarsaathi.app.data.ask.AskEngine;
import com.safarsaathi.app.data.ask.AskLanguage;
import com.safarsaathi.app.data.ask.AskSettings;
import com.safarsaathi.app.data.ask.LanguageHint;
import com.safarsaathi.app.data.ask.MlKitLanguageHints;
import com.safarsaathi.app.data.ask.TripFacts;
import com.safarsaathi.app.data.local.AirportMapEntity;
import com.safarsaathi.app.data.local.BlockEntity;
import com.safarsaathi.app.data.local.TripEntity;
import com.safarsaathi.routing.map.AirportMap;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Ask OnBoard screen's data: the chat (in memory only, gone when the screen closes) and the reply language.
 *
 * <p>Where answers come from (Layer 1, Quick answers, fully offline):
 * <ol>
 *   <li>The saved trip and the terminal map, watched from Room exactly like the Home and Navigate tabs watch them, so
 *       an answer and the Home card always agree. The latest copies are kept here.</li>
 *   <li>When the traveller asks, a background thread ("onboard-ask") asks ML Kit for a language hint, then
 *       {@link AskEngine} picks the reply language and intent and fills the answer from a {@link TripFacts} snapshot.
 *       Nothing goes to the internet.</li>
 * </ol>
 * A question asked before Room has answered once (a few milliseconds after the screen opens) waits for it.
 */
public class AskViewModel extends AndroidViewModel {

    private static final String TAG = "AskOnBoard";

    private final TripRepository tripRepository = ServiceLocator.get().getTripRepository();
    private final AirportMapRepository mapRepository = ServiceLocator.get().getAirportMapRepository();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "onboard-ask"));
    private final Clock clock = Clock.systemUTC();

    private final MutableLiveData<List<ChatMessage>> messages =
            new MutableLiveData<>(Collections.<ChatMessage>emptyList());
    /** The traveller's chosen reply language; null = automatic. */
    private final MutableLiveData<AskLanguage> chosenLanguage = new MutableLiveData<>();
    /** The language of the latest answer, for the quick-question chips and the speech recognizer. */
    private final MutableLiveData<AskLanguage> conversationLanguage = new MutableLiveData<>();
    /** An answer to read aloud (the question came by voice); the screen clears it after speaking. */
    private final MutableLiveData<Answer> toSpeak = new MutableLiveData<>();

    // Written by the Room observers (main thread), read by the worker thread: volatile.
    private volatile TripEntity trip;
    private volatile List<BlockEntity> blocks = Collections.emptyList();
    private volatile AirportMap map;
    private boolean tripLoaded;
    private boolean blocksLoaded;
    private int mapVersion = -1;

    // Created on the worker thread: the lexicon files and ML Kit's model are read from disk.
    private AskEngine engine;
    private MlKitLanguageHints hints;

    private final List<Runnable> waitingForData = new ArrayList<>();
    private long nextId = 1;

    private final Observer<TripEntity> tripObserver = newTrip -> {
        trip = newTrip;
        tripLoaded = true;
        runWaiting();
    };
    private final Observer<List<BlockEntity>> blocksObserver = newBlocks -> {
        blocks = newBlocks == null ? Collections.<BlockEntity>emptyList() : newBlocks;
        blocksLoaded = true;
        runWaiting();
    };
    private final Observer<AirportMapEntity> mapObserver = this::onMapSaved;

    private final LiveData<TripEntity> tripLive;
    private final LiveData<List<BlockEntity>> blocksLive;
    private final LiveData<AirportMapEntity> mapLive;

    public AskViewModel(@NonNull Application application) {
        super(application);
        AskLanguage chosen = AskSettings.replyLanguage(application);
        chosenLanguage.setValue(chosen);
        conversationLanguage.setValue(chosen != null ? chosen : AskLanguage.forAppLanguage(
                application.getResources().getConfiguration().getLocales().get(0).getLanguage()));

        tripLive = tripRepository.observeTrip(AppConfig.DEMO_TRIP_ID);
        blocksLive = tripRepository.observeBlocks(AppConfig.DEMO_TRIP_ID);
        mapLive = mapRepository.observe(AppConfig.PILOT_AIRPORT, AppConfig.PILOT_TERMINAL);
        tripLive.observeForever(tripObserver);
        blocksLive.observeForever(blocksObserver);
        mapLive.observeForever(mapObserver);
        // The map ships inside the app: save it to Room if this phone has never opened the Navigate tab.
        mapRepository.ensureSaved(AppConfig.PILOT_AIRPORT, AppConfig.PILOT_TERMINAL);
        worker.execute(() -> {
            engine = AskEngine.standard();
            hints = new MlKitLanguageHints();
        });
    }

    LiveData<List<ChatMessage>> getMessages() {
        return messages;
    }

    LiveData<AskLanguage> getChosenLanguage() {
        return chosenLanguage;
    }

    LiveData<AskLanguage> getConversationLanguage() {
        return conversationLanguage;
    }

    LiveData<Answer> getToSpeak() {
        return toSpeak;
    }

    void spoken() {
        toSpeak.setValue(null);
    }

    /** The language chip: a fixed reply language, or null for automatic. Remembered on the phone. */
    void chooseLanguage(@Nullable AskLanguage language) {
        AskSettings.setReplyLanguage(getApplication(), language);
        chosenLanguage.setValue(language);
        if (language != null) {
            conversationLanguage.setValue(language);
        }
    }

    /**
     * The traveller asked something (typed, a chip, or heard by the speech recognizer). Shows the question at once and
     * the answer a moment later. Main thread only.
     */
    void ask(String rawQuestion, boolean byVoice) {
        String question = rawQuestion == null ? "" : rawQuestion.trim();
        if (question.isEmpty()) {
            return;
        }
        add(ChatMessage.question(nextId++, question));
        long answerId = nextId++;
        AskLanguage override = chosenLanguage.getValue();
        Runnable work = () -> worker.execute(() -> answer(question, override, byVoice, answerId));
        if (tripLoaded && blocksLoaded) {
            work.run();
        } else {
            waitingForData.add(work);
        }
    }

    /** Worker thread: builds the answer from a snapshot of the saved data. */
    private void answer(String question, @Nullable AskLanguage override, boolean byVoice, long answerId) {
        LanguageHint hint = hints == null ? LanguageHint.NONE : hints.hint(question);
        TripFacts facts = TripFacts.from(trip, blocks, map, clock.instant());
        Answer answer = engine.ask(question, override, hint, facts);
        // Only the intent and language are logged, never the question or the answer (they may be personal).
        Log.i(TAG, "Layer 1 answered " + answer.intent + " (" + answer.confidence + ") in " + answer.language);
        ContextCompat.getMainExecutor(getApplication()).execute(() -> {
            add(ChatMessage.answer(answerId, answer));
            conversationLanguage.setValue(answer.language);
            if (byVoice) {
                toSpeak.setValue(answer);
            }
        });
    }

    private void add(ChatMessage message) {
        List<ChatMessage> list = new ArrayList<>(messages.getValue());
        list.add(message);
        messages.setValue(Collections.unmodifiableList(list));
    }

    private void runWaiting() {
        if (!tripLoaded || !blocksLoaded) {
            return;
        }
        List<Runnable> ready = new ArrayList<>(waitingForData);
        waitingForData.clear();
        for (Runnable work : ready) {
            work.run();
        }
    }

    /** Room has a (new) saved map: read it on the worker thread, only when its version changed. */
    private void onMapSaved(@Nullable AirportMapEntity saved) {
        if (saved == null || saved.version == mapVersion) {
            return;
        }
        mapVersion = saved.version;
        String json = saved.json;
        worker.execute(() -> {
            try {
                map = MapJson.parse(json).map;
            } catch (RuntimeException e) {
                Log.w(TAG, "Saved terminal map could not be read; 'where is' answers will say so", e);
            }
        });
    }

    @Override
    protected void onCleared() {
        tripLive.removeObserver(tripObserver);
        blocksLive.removeObserver(blocksObserver);
        mapLive.removeObserver(mapObserver);
        worker.execute(() -> {
            if (hints != null) {
                hints.close();
            }
        });
        worker.shutdown();
        super.onCleared();
    }
}
