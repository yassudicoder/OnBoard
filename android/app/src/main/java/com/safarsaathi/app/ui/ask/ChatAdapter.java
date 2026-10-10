package com.safarsaathi.app.ui.ask;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.safarsaathi.app.data.ask.Answer;
import com.safarsaathi.app.data.ask.AnswerText;
import com.safarsaathi.app.data.ask.QuickAnswerer;
import com.safarsaathi.app.databinding.ItemAskAnswerBinding;
import com.safarsaathi.app.databinding.ItemAskQuestionBinding;

import java.time.Instant;

/**
 * The chat's bubbles: questions (right) and answers (left). Under every answer: where it came from, in the answer's
 * own language ("Answered on your phone · as of 05:42"), a speaker button, and the answer's button when it has one.
 */
final class ChatAdapter extends ListAdapter<ChatMessage, RecyclerView.ViewHolder> {

    interface Callbacks {
        void onAction(Answer.Action action);

        void onSpeak(Answer answer);
    }

    private static final int TYPE_QUESTION = 0;
    private static final int TYPE_ANSWER = 1;

    private static final DiffUtil.ItemCallback<ChatMessage> DIFF = new DiffUtil.ItemCallback<ChatMessage>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            return oldItem.equals(newItem);
        }
    };

    private final Callbacks callbacks;

    ChatAdapter(Callbacks callbacks) {
        super(DIFF);
        this.callbacks = callbacks;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isQuestion() ? TYPE_QUESTION : TYPE_ANSWER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_QUESTION) {
            return new QuestionHolder(ItemAskQuestionBinding.inflate(inflater, parent, false));
        }
        return new AnswerHolder(ItemAskAnswerBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = getItem(position);
        if (holder instanceof QuestionHolder) {
            ((QuestionHolder) holder).binding.askQuestionText.setText(message.text);
        } else {
            ((AnswerHolder) holder).bind(message.answer);
        }
    }

    private static final class QuestionHolder extends RecyclerView.ViewHolder {
        final ItemAskQuestionBinding binding;

        QuestionHolder(ItemAskQuestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private final class AnswerHolder extends RecyclerView.ViewHolder {
        final ItemAskAnswerBinding binding;

        AnswerHolder(ItemAskAnswerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Answer answer) {
            AnswerText text = AnswerText.of(answer.language);
            binding.askAnswerText.setText(answer.text);
            binding.askAnswerSource.setText(sourceLine(answer, text));
            binding.askAnswerSpeak.setOnClickListener(button -> callbacks.onSpeak(answer));
            boolean hasAction = answer.action != null;
            binding.askAnswerAction.setVisibility(hasAction ? View.VISIBLE : View.GONE);
            if (hasAction) {
                binding.askAnswerAction.setText(text.get("action." + answer.action.name()));
                binding.askAnswerAction.setOnClickListener(button -> callbacks.onAction(answer.action));
            }
        }
    }

    /** "Answered on your phone · as of 05:42", in the answer's language; no "as of" for fixed data (the map). */
    static String sourceLine(Answer answer, AnswerText text) {
        String source = text.get("source.phone");
        if (answer.asOf == null) {
            return source;
        }
        String time = QuickAnswerer.time(answer.asOf, text, Instant.now());
        return source + " · " + text.fill("source.as_of", "time", time);
    }
}
