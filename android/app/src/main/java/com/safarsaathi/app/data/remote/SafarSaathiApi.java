package com.safarsaathi.app.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * Our backend's REST API, as Java (full list in docs/api.md). Retrofit writes the implementation:
 * {@code retrofit.create(SafarSaathiApi.class)} in ServiceLocator.
 *
 * <p>Paths have no leading "/": they are added to the base URL (e.g. http://10.0.2.2:8080/), which
 * already ends with "/".
 */
public interface SafarSaathiApi {

    /** The full trip: trip fields, every block and every link. */
    @GET("api/trips/{id}")
    Call<TripDto> getTrip(@Path("id") long tripId);
}
