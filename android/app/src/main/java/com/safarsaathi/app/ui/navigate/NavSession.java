package com.safarsaathi.app.ui.navigate;

import com.safarsaathi.routing.map.AirportMap;
import com.safarsaathi.routing.map.MapNode;
import com.safarsaathi.routing.map.NodeType;
import com.safarsaathi.routing.route.Maneuver;
import com.safarsaathi.routing.route.Route;
import com.safarsaathi.routing.route.RouteLeg;
import com.safarsaathi.routing.route.RoutePoint;
import com.safarsaathi.routing.route.RouteStep;
import com.safarsaathi.routing.route.Router;
import com.safarsaathi.routing.route.WalkProfile;

import java.util.Map;

/**
 * The Navigate tab's state, Google-Maps style, as plain Java (no Android; checked by NavSessionTest):
 * EXPLORE (look at the map) → PREVIEW (a route from "you are here" to a place, with time and distance) →
 * NAVIGATING (turn-by-turn, the blue arrow moves along the route) → ARRIVED.
 *
 * <p><b>Where the traveller is (honest positioning, CLAUDE.md):</b> phones can't locate themselves inside a terminal,
 * so the position comes from
 * <ol>
 *   <li>a QR "You are here" code or the traveller choosing their place: exact, a map place;</li>
 *   <li>counting steps along the route ({@link #walk}): an estimate, which a turn the phone's motion sensors notice
 *       near the next corner corrects ({@link #onHeading});</li>
 *   <li>a simulated walk for demos ({@link #walkFor}).</li>
 * </ol>
 * A QR code or a chosen place that is NOT on the route means the traveller went another way: the route is worked out
 * again from there (re-route), and the screen says so.
 */
public final class NavSession {

    public enum Mode { EXPLORE, PREVIEW, NAVIGATING, ARRIVED }

    /** How the app knows where the traveller is. */
    public enum HereSource {
        /** Scanned a QR "You are here" code: exact. */
        QR,
        /** The traveller picked their place: exact enough. */
        CHOSEN,
        /** Moved along a route (counted steps or the demo walk): an estimate. */
        WALKED
    }

    /** What a call changed, for the screen to say out loud or show. */
    public enum Event {
        NONE,
        /** A route could not be found (e.g. step-free with no lift). */
        NO_ROUTE,
        /** Position put back exactly on the route (QR code or chosen place on the route). */
        POSITION_CONFIRMED,
        /** The traveller was not on the route any more: new route from where they are. */
        REROUTED,
        /** The route changed because a setting changed (step-free, pace). */
        ROUTE_UPDATED,
        /** The motion sensors noticed the expected turn: the position jumped to that corner. */
        TURN_CONFIRMED,
        ARRIVED
    }

    /** Within this many metres before a turn, a matching turn of the phone moves the position to the corner. */
    static final double TURN_WINDOW_M = 20;
    /** Closer than this to the destination counts as arrived (counted steps are never exact). */
    static final double ARRIVED_WITHIN_M = 3;
    /** A "Head towards ..." stays in the banner for this many metres before the next turn takes over. */
    static final double DEPART_BANNER_M = 8;
    /** "Then ..." is shown when the step after the next one follows within this many metres. */
    static final double THEN_WITHIN_M = 60;

    private AirportMap map;
    private Router router;
    private WalkProfile profile = WalkProfile.normal();
    private Mode mode = Mode.EXPLORE;

    private String hereId;
    private HereSource hereSource;
    private String destinationId;
    /** Where to go after a detour (e.g. washrooms on the way to the gate); null when there is none. */
    private String resumeId;

    private Route route;
    private double progress;
    /** Goes up every time the route changes, so the screen knows to redraw and announce. */
    private int routeVersion;
    /** The place where step counting started (a QR code, a chosen place, or where the route began). */
    private String countedFromId;

    /** False once the position comes from counted steps / the demo walk instead of a QR code or chosen place. */
    private boolean exactFix;

    private double headingReference = Double.NaN;
    private int headingStepIndex = -1;

    // ---- Map and settings ----

    /** A (new) map. Places that no longer exist are forgotten; a running route is worked out again. */
    public void setMap(AirportMap newMap) {
        if (newMap == null || newMap == map) {
            return;
        }
        boolean sameVersion = map != null && map.version == newMap.version;
        map = newMap;
        router = new Router(newMap);
        if (sameVersion && route != null) {
            return;
        }
        if (hereId != null && newMap.node(hereId) == null) {
            hereId = null;
        }
        if (destinationId != null && newMap.node(destinationId) == null) {
            destinationId = null;
            resumeId = null;
            mode = Mode.EXPLORE;
            route = null;
        }
        if (mode == Mode.PREVIEW) {
            planFromHere();
        } else if (mode == Mode.NAVIGATING) {
            String from = currentNodeId();
            mode = Mode.PREVIEW;
            hereId = from;
            if (planFromHere() == Event.NONE) {
                mode = Mode.NAVIGATING;
                progress = 0;
            }
        }
    }

    public AirportMap map() {
        return map;
    }

    public WalkProfile profile() {
        return profile;
    }

    /** New pace / step-free choice: the route is worked out again (from where the traveller is now). */
    public Event setProfile(WalkProfile newProfile) {
        if (newProfile.walkSpeed == profile.walkSpeed && newProfile.stepFree == profile.stepFree) {
            return Event.NONE;
        }
        profile = newProfile;
        if (mode == Mode.PREVIEW) {
            return planFromHere() == Event.NONE ? Event.ROUTE_UPDATED : Event.NO_ROUTE;
        }
        if (mode == Mode.NAVIGATING) {
            return rerouteFrom(currentNodeId(), Event.ROUTE_UPDATED);
        }
        return Event.NONE;
    }

    // ---- Where the traveller is ----

    /** The traveller is at this place (scanned its QR code, or chose it). */
    public Event setHere(String nodeId, HereSource source) {
        requireNode(nodeId);
        if (mode == Mode.ARRIVED) {
            mode = Mode.EXPLORE;
            route = null;
        }
        if (mode == Mode.NAVIGATING) {
            double along = route.metresAtNode(nodeId);
            hereId = nodeId;
            hereSource = source;
            countedFromId = nodeId;
            exactFix = source != HereSource.WALKED;
            if (along >= 0) {
                progress = along;
                resetHeading();
                return isAtDestination() ? arrive() : Event.POSITION_CONFIRMED;
            }
            return rerouteFrom(nodeId, Event.REROUTED);
        }
        hereId = nodeId;
        hereSource = source;
        if (mode == Mode.PREVIEW) {
            return planFromHere();
        }
        return Event.NONE;
    }

    /** The place the traveller is at (or last passed), or null when unknown. */
    public MapNode here() {
        return hereId == null || map == null ? null : map.node(hereId);
    }

    public HereSource hereSource() {
        return hereSource;
    }

    /** Where to draw the traveller: on the route while navigating, else at their place; null when unknown. */
    public RoutePoint position() {
        if ((mode == Mode.NAVIGATING) && route != null) {
            return route.pointAt(progress);
        }
        return null;
    }

    /** True when the position is a guess from counted steps or the demo walk, not a QR code / chosen place. */
    public boolean positionIsEstimate() {
        if (mode == Mode.NAVIGATING) {
            return !exactFix;
        }
        return hereSource == HereSource.WALKED;
    }

    /** The place step counting started from (for "counted from Entry Gate 4"). */
    public MapNode countedFrom() {
        return countedFromId == null || map == null ? null : map.node(countedFromId);
    }

    // ---- Where to ----

    /** Plan a route to a place: shows the preview (a route as soon as the traveller's place is known). */
    public Event setDestination(String nodeId) {
        requireNode(nodeId);
        destinationId = nodeId;
        resumeId = null;
        if (mode == Mode.ARRIVED) {
            route = null;
        }
        mode = Mode.PREVIEW;
        return planFromHere();
    }

    /** A stop on the way (e.g. washrooms while walking to the gate); afterwards "Continue to Gate 32". */
    public Event detourTo(String nodeId) {
        requireNode(nodeId);
        if (mode != Mode.NAVIGATING) {
            return setDestination(nodeId);
        }
        if (resumeId == null) {
            resumeId = destinationId;
        }
        destinationId = nodeId;
        return rerouteFrom(currentNodeId(), Event.ROUTE_UPDATED);
    }

    public MapNode destination() {
        return destinationId == null || map == null ? null : map.node(destinationId);
    }

    /** Where to go after a detour, or null. */
    public MapNode resumeDestination() {
        return resumeId == null || map == null ? null : map.node(resumeId);
    }

    /** After arriving at a detour stop: navigate on to the original destination at once. */
    public Event continueToResume() {
        if (resumeId == null) {
            return Event.NONE;
        }
        String next = resumeId;
        resumeId = null;
        destinationId = next;
        mode = Mode.PREVIEW;
        Event event = planFromHere();
        if (event == Event.NONE) {
            start();
        }
        return event;
    }

    // ---- Starting and stopping ----

    /** "Start": from the preview into turn-by-turn. False when there is no route yet. */
    public boolean start() {
        if (mode != Mode.PREVIEW || route == null) {
            return false;
        }
        mode = Mode.NAVIGATING;
        progress = 0;
        countedFromId = hereId;
        exactFix = hereSource != HereSource.WALKED;
        resetHeading();
        return true;
    }

    /** Back to looking at the map. While navigating, the traveller stays where the route had got to. */
    public void stop() {
        if (mode == Mode.NAVIGATING && route != null) {
            hereId = currentNodeId();
            hereSource = HereSource.WALKED;
        }
        mode = Mode.EXPLORE;
        route = null;
        resumeId = null;
        destinationId = null;
        progress = 0;
        routeVersion++;
    }

    public Mode mode() {
        return mode;
    }

    public Route route() {
        return route;
    }

    public int routeVersion() {
        return routeVersion;
    }

    /** Metres walked along the route. */
    public double progress() {
        return progress;
    }

    // ---- Moving ----

    /** The traveller walked this many metres (counted steps). */
    public Event walk(double metres) {
        if (mode != Mode.NAVIGATING || route == null) {
            return Event.NONE;
        }
        progress = route.clamp(progress + metres);
        exactFix = false;
        return isAtDestination() ? arrive() : Event.NONE;
    }

    /** The demo walk: moves along the route as far as the profile's pace gets in this many seconds. */
    public Event walkFor(double seconds) {
        if (mode != Mode.NAVIGATING || route == null) {
            return Event.NONE;
        }
        progress = route.metresAt(route.secondsAt(progress) + seconds);
        exactFix = false;
        return isAtDestination() ? arrive() : Event.NONE;
    }

    /**
     * The phone's heading (degrees clockwise, from the motion sensors). Only CHANGES of heading are used, so the
     * map does not have to know where real north is: near the next turn, turning the expected way (left / right, by
     * at least 60% of the turn) moves the position to that corner. That corrects step counting, which drifts.
     */
    public Event onHeading(double degrees) {
        if (mode != Mode.NAVIGATING || route == null || Double.isNaN(degrees)) {
            return Event.NONE;
        }
        int stepIndex = route.stepIndexAt(progress);
        if (stepIndex != headingStepIndex) {
            headingStepIndex = stepIndex;
            headingReference = Double.NaN;
        }
        RouteStep next = stepIndex + 1 < route.steps.size() ? route.steps.get(stepIndex + 1) : null;
        double toTurn = next == null ? Double.MAX_VALUE : next.startMetres - progress;
        if (next == null || !next.maneuver.isTurn() || toTurn > TURN_WINDOW_M || Double.isNaN(headingReference)) {
            headingReference = degrees; // walking straight on: keep following the phone's direction
            return Event.NONE;
        }
        double turned = signedDifference(headingReference, degrees);
        boolean matches;
        if (next.maneuver == Maneuver.U_TURN) {
            matches = Math.abs(turned) >= 120;
        } else {
            double needed = Math.max(30, 0.6 * Math.abs(next.turnDegrees));
            matches = Math.signum(turned) == Math.signum(next.turnDegrees) && Math.abs(turned) >= needed;
        }
        if (!matches) {
            return Event.NONE;
        }
        progress = route.clamp(next.startMetres + 0.5);
        headingReference = degrees;
        headingStepIndex = route.stepIndexAt(progress);
        return isAtDestination() ? arrive() : Event.TURN_CONFIRMED;
    }

    // ---- What the screen shows while navigating ----

    /** The instruction in the green banner: the next turn, or "Head towards ..." just after starting / a lift. */
    public RouteStep bannerStep() {
        if (route == null || route.steps.isEmpty()) {
            return null;
        }
        int index = route.stepIndexAt(progress);
        RouteStep current = route.steps.get(index);
        boolean justStarted = current.maneuver == Maneuver.DEPART
                && progress - current.startMetres < DEPART_BANNER_M;
        boolean riding = current.maneuver.changesFloor() && onRideLeg();
        if (justStarted || riding || index + 1 >= route.steps.size()) {
            return current;
        }
        return route.steps.get(index + 1);
    }

    /** Metres to the banner's instruction (for "Head towards ...": to the next turn after it). */
    public double bannerDistance() {
        RouteStep banner = bannerStep();
        if (banner == null) {
            return 0;
        }
        if (banner.startMetres <= progress + 0.01) {
            int index = route.steps.indexOf(banner);
            RouteStep after = index + 1 < route.steps.size() ? route.steps.get(index + 1) : null;
            return after == null ? 0 : Math.max(0, after.startMetres - progress);
        }
        return Math.max(0, banner.startMetres - progress);
    }

    /** The step after the banner's, when it comes soon after it ("Then ..."), else null. */
    public RouteStep thenStep() {
        RouteStep banner = bannerStep();
        if (banner == null) {
            return null;
        }
        int index = route.steps.indexOf(banner);
        if (index + 1 >= route.steps.size()) {
            return null;
        }
        RouteStep then = route.steps.get(index + 1);
        return then.startMetres - Math.max(banner.startMetres, progress) <= THEN_WITHIN_M ? then : null;
    }

    public double remainingMetres() {
        return route == null ? 0 : Math.max(0, route.totalMetres - progress);
    }

    /** Estimated seconds still to walk (from the profile's pace; no queues). */
    public double remainingSeconds() {
        return route == null ? 0 : Math.max(0, route.totalSeconds - route.secondsAt(progress));
    }

    // ---- Help nearby ----

    /** The place the traveller is at or nearest to on the route; null when unknown. */
    public MapNode currentPlace() {
        String id = mode == Mode.NAVIGATING ? currentNodeId() : hereId;
        return id == null || map == null ? null : map.node(id);
    }

    /** The quickest-to-reach place of this kind from where the traveller is (e.g. washrooms), or null. */
    public MapNode nearest(NodeType type) {
        MapNode from = currentPlace();
        if (from == null || router == null) {
            return null;
        }
        for (Map.Entry<MapNode, Double> entry : router.secondsFrom(from.id, profile).entrySet()) {
            if (entry.getKey().type == type) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** A route from where the traveller is to a place (for "2 min · 120 m" in lists), or null. */
    public Route routeFromHere(MapNode to) {
        MapNode from = currentPlace();
        return from == null || router == null ? null : router.route(from.id, to.id, profile);
    }

    // ---- Inside ----

    private Event planFromHere() {
        routeVersion++;
        progress = 0;
        resetHeading();
        if (hereId == null || destinationId == null || router == null) {
            route = null;
            return Event.NONE;
        }
        route = router.route(hereId, destinationId, profile);
        return route == null ? Event.NO_ROUTE : Event.NONE;
    }

    private Event rerouteFrom(String nodeId, Event success) {
        Route newRoute = router.route(nodeId, destinationId, profile);
        routeVersion++;
        resetHeading();
        hereId = nodeId;
        countedFromId = nodeId;
        if (newRoute == null) {
            // Keep the old route on screen; the traveller can change the settings back or choose another place.
            return Event.NO_ROUTE;
        }
        route = newRoute;
        progress = 0;
        return isAtDestination() ? arrive() : success;
    }

    /** The route place nearest to the current position (the end of the current walkway once past its middle). */
    private String currentNodeId() {
        if (route == null || route.legs.isEmpty()) {
            return hereId;
        }
        int index = route.legIndexAt(progress);
        RouteLeg leg = route.legs.get(index);
        double into = progress - leg.startMetres;
        return into > leg.metres / 2 ? leg.to.id : leg.from.id;
    }

    private boolean onRideLeg() {
        int index = route.legIndexAt(progress);
        return index >= 0 && route.legs.get(index).changesFloor();
    }

    private boolean isAtDestination() {
        return route != null && route.totalMetres - progress <= ARRIVED_WITHIN_M;
    }

    private Event arrive() {
        progress = route.totalMetres;
        mode = Mode.ARRIVED;
        hereId = destinationId;
        hereSource = HereSource.WALKED;
        return Event.ARRIVED;
    }

    private void resetHeading() {
        headingReference = Double.NaN;
        headingStepIndex = -1;
    }

    private void requireNode(String nodeId) {
        if (map == null || map.node(nodeId) == null) {
            throw new IllegalArgumentException("No place '" + nodeId + "' on the map");
        }
    }

    /** to - from in degrees, in (-180, 180]: + = turned right (clockwise). */
    static double signedDifference(double from, double to) {
        double difference = (to - from) % 360;
        if (difference > 180) {
            difference -= 360;
        } else if (difference <= -180) {
            difference += 360;
        }
        return difference;
    }
}
