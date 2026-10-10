package com.safarsaathi.app.testing;

import androidx.annotation.NonNull;

import com.safarsaathi.app.data.remote.SafarSaathiApi;
import com.safarsaathi.app.data.remote.TripDto;

import java.io.IOException;

import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A fake backend that is always unreachable, as if the phone were in airplane mode. UI tests use it so they
 * give the same result whether or not a real backend happens to run on the laptop.
 */
public class OfflineApi implements SafarSaathiApi {

    @Override
    public Call<TripDto> getTrip(long tripId) {
        return new NoNetworkCall<>();
    }

    @Override
    public Call<ResponseBody> getAirportMap(String airport, String terminal) {
        return new NoNetworkCall<>();
    }

    /** A Retrofit call that fails at once with "no network". */
    private static final class NoNetworkCall<T> implements Call<T> {
        private boolean executed;
        private boolean canceled;

        @Override
        public Response<T> execute() throws IOException {
            executed = true;
            throw new IOException("Test: no network");
        }

        @Override
        public void enqueue(@NonNull Callback<T> callback) {
            executed = true;
            callback.onFailure(this, new IOException("Test: no network"));
        }

        @Override
        public boolean isExecuted() {
            return executed;
        }

        @Override
        public void cancel() {
            canceled = true;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        @NonNull
        @Override
        public Call<T> clone() {
            return new NoNetworkCall<>();
        }

        @NonNull
        @Override
        public Request request() {
            return new Request.Builder().url("http://offline.test/").build();
        }

        @NonNull
        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }
    }
}
