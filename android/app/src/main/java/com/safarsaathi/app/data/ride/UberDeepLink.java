package com.safarsaathi.app.data.ride;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

/**
 * Builds an Uber "universal link" that opens Uber (the app, or Uber's mobile website when the app is missing) with
 * the pickup and drop-off already filled in. Price, availability and the booking itself all happen inside Uber:
 * we never learn whether a ride was booked. Plain Java, checked by UberDeepLinkTest.
 *
 * <p>Format, checked on 2026-10-11 against two official Uber sources:
 * <ul>
 *   <li>Uber's own Android SDK (github.com/uber/rides-android-sdk): {@code Deeplink.APP_LINK_URI =
 *       "https://m.uber.com/ul/"}, and {@code RideRequestDeeplink} adds {@code action=setPickup}, {@code client_id},
 *       then {@code pickup[latitude]}, {@code pickup[longitude]}, {@code pickup[nickname]},
 *       {@code pickup[formatted_address]} (or {@code pickup=my_location}), then the same {@code dropoff[...]} keys.</li>
 *   <li>The deep-link guide (developer.uber.com/docs/riders/ride-requests/tutorials/deep-links/introduction): its
 *       {@code action=setPickup} example uses exactly these keys, {@code pickup=my_location} for "the rider's current
 *       location", and encodes the brackets and spaces ({@code dropoff%5Bformatted_address%5D=Uber%20HQ%2C...}).</li>
 * </ul>
 * The guide also shows a newer {@code https://m.uber.com/looking?pickup={json}&drop[0]={json}} form. We use the
 * {@code /ul/} form above because Uber's SDK builds it and it takes plain keys instead of JSON.
 *
 * <p>Not documented by Uber, so not assumed here: whether a drop-off WITHOUT coordinates (a typed address only) gets
 * looked up by Uber. We still send it, and the screen asks the traveller to check the drop-off in Uber.
 */
public final class UberDeepLink {

    /** Uber's universal link (Uber's Android SDK, Deeplink.APP_LINK_URI). */
    public static final String BASE = "https://m.uber.com/ul/";
    /** {@code pickup=my_location}: Uber uses the phone's current position. */
    public static final String MY_LOCATION = "my_location";

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private UberDeepLink() {
    }

    /**
     * @param clientId our Uber app's client id, or null/blank when the team has none (the link then simply has no
     *                 client_id; Uber uses it to credit sign-ups to our developer account, the link opens without it)
     * @param pickup   where from; an empty place is treated as "my location"
     * @param dropoff  where to; must not be empty (the button is disabled in that case, see RideCheck)
     * @throws IllegalArgumentException when there is no drop-off
     */
    public static String build(@Nullable String clientId, RidePlace pickup, RidePlace dropoff) {
        if (dropoff == null || dropoff.isEmpty() || dropoff.myLocation) {
            throw new IllegalArgumentException("A ride link needs a drop-off place");
        }
        StringBuilder link = new StringBuilder(BASE).append("?action=setPickup");
        if (clientId != null && !clientId.trim().isEmpty()) {
            add(link, "client_id", clientId.trim());
        }
        if (pickup == null || pickup.myLocation || pickup.isEmpty()) {
            add(link, "pickup", MY_LOCATION);
        } else {
            addPlace(link, "pickup", pickup);
        }
        addPlace(link, "dropoff", dropoff);
        return link.toString();
    }

    /** pickup[latitude]=..&pickup[longitude]=..&pickup[nickname]=..&pickup[formatted_address]=.. (what is known). */
    private static void addPlace(StringBuilder link, String prefix, RidePlace place) {
        if (place.hasCoordinates()) {
            add(link, prefix + "[latitude]", coordinate(place.lat));
            add(link, prefix + "[longitude]", coordinate(place.lng));
        }
        if (place.name != null) {
            add(link, prefix + "[nickname]", place.name);
        }
        if (place.address != null) {
            add(link, prefix + "[formatted_address]", place.address);
        }
    }

    private static void add(StringBuilder link, String key, String value) {
        link.append('&').append(encode(key)).append('=').append(encode(value));
    }

    /**
     * 6 decimals (about 10 cm) and always a dot. Locale-independent on purpose: String.format on a phone set to
     * some languages would write "19,096991", which Uber would not understand.
     */
    static String coordinate(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * Percent-encoding for a URL query (RFC 3986): letters, digits and "-._~" stay, every other byte of the UTF-8
     * text becomes %XX. So a space is %20 (as in Uber's examples, not "+"), "[" is %5B, and Hindi or Marathi
     * names become their UTF-8 bytes, e.g. "मुंबई" -> %E0%A4%AE....
     */
    static String encode(String text) {
        StringBuilder out = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~';
            if (unreserved) {
                out.append((char) c);
            } else {
                out.append('%').append(HEX[c >> 4]).append(HEX[c & 0x0F]);
            }
        }
        return out.toString();
    }
}
