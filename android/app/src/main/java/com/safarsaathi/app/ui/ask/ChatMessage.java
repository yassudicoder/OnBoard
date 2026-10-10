package com.safarsaathi.app.ui.ask;

import androidx.annotation.Nullable;

import com.safarsaathi.app.data.ask.Answer;

import java.util.Objects;

/** One bubble in the Ask OnBoard chat: the traveller's question, or OnBoard's answer. Kept in memory only. */
final class ChatMessage {

    final long id;
    /** The traveller's words; for an answer, the answer's text. */
    final String text;
    /** Null for a question. */
    @Nullable
    final Answer answer;

    private ChatMessage(long id, String text, @Nullable Answer answer) {
        this.id = id;
        this.text = text;
        this.answer = answer;
    }

    static ChatMessage question(long id, String text) {
        return new ChatMessage(id, text, null);
    }

    static ChatMessage answer(long id, Answer answer) {
        return new ChatMessage(id, answer.text, answer);
    }

    boolean isQuestion() {
        return answer == null;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ChatMessage)) {
            return false;
        }
        ChatMessage that = (ChatMessage) other;
        return id == that.id && text.equals(that.text) && answer == that.answer;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text);
    }
}
