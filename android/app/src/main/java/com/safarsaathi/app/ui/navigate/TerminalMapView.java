package com.safarsaathi.app.ui.navigate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import com.safarsaathi.app.R;
import com.safarsaathi.app.data.airport.MapArea;
import com.safarsaathi.app.data.airport.MapJson;
import com.safarsaathi.routing.map.EdgeKind;
import com.safarsaathi.routing.map.MapEdge;
import com.safarsaathi.routing.map.MapFloor;
import com.safarsaathi.routing.map.MapNode;
import com.safarsaathi.routing.map.NodeType;
import com.safarsaathi.routing.route.Route;
import com.safarsaathi.routing.route.RouteLeg;
import com.safarsaathi.routing.route.RoutePoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Draws one floor of the terminal map in a Google-Maps-like style (our own drawing on a Canvas: no map library, no
 * internet, no key):
 * <ul>
 *   <li>halls and piers as soft shapes, walkways as white "corridors" with a grey edge (travelators with arrows);</li>
 *   <li>places as white icons on coloured circles, gates as blue number badges, names with a white halo; labels never
 *       overlap (the most important win, more appear as you zoom in);</li>
 *   <li>the route as a thick blue line, the destination as a red pin, the traveller as a blue dot, or while
 *       navigating as a blue arrow that the map follows and turns with ("heading up", like Google Maps).</li>
 * </ul>
 * Drag to move, pinch or double-tap to zoom, tap a place ({@link Listener#onPlaceTapped}). Dragging while the map
 * follows the traveller stops following ({@link Listener#onFollowStopped}), and the screen offers "Re-centre".
 * Routing never depends on this view: it only draws what it is given. Screen readers use the fragment's place list.
 */
public class TerminalMapView extends View {

    public interface Listener {
        /** The traveller tapped a place (null = empty space). */
        void onPlaceTapped(@Nullable MapNode node);

        /** The traveller moved the map by hand while it was following them. */
        void onFollowStopped();
    }

    /** Width of a walkway as drawn, in metres. */
    private static final float CORRIDOR_WIDTH_M = 5f;
    /** While navigating, about this many metres fit across the screen. */
    private static final float FOLLOW_WIDTH_M = 70f;
    /** While navigating, the arrow sits this far down the free part of the screen. */
    private static final float FOLLOW_ANCHOR = 0.68f;

    private final MapViewport viewport = new MapViewport();
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaler;

    private final Paint areaFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corridorFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corridorEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint travelatorFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint travelatorArrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routeEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelHalo = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint areaText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint areaHalo = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint badgeText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint chipText = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final int background;
    private final int landsideFill;
    private final int airsideFill;
    private final int checkpointFill;
    private final int userBlue;
    private final int pinRed;
    private final int pinRedDark;
    private final Map<NodeType, Drawable> icons = new EnumMap<>(NodeType.class);
    private final Map<NodeType, Integer> iconColors = new EnumMap<>(NodeType.class);

    // ---- What to draw ----
    @Nullable
    private MapJson.MapData data;
    private String floorId = "";
    private String language = "en";
    private List<MapNode> floorNodes = Collections.emptyList();
    private List<MapEdge> floorEdges = Collections.emptyList();
    private List<MapArea> floorAreas = Collections.emptyList();
    @Nullable
    private MapNode selected;
    @Nullable
    private MapNode here;
    private boolean hereEstimate;
    @Nullable
    private MapNode destination;
    @Nullable
    private Route route;
    private double progress;
    @Nullable
    private RoutePoint user;
    private boolean navigating;
    @Nullable
    private Listener listener;

    // ---- Camera ----
    private boolean following;
    private float followScale;
    private boolean animating;
    private double targetX;
    private double targetY;
    private float targetScale;
    private float targetBearing;
    private float overlayTop;
    private float overlayBottom;
    private boolean fitPending = true;
    /** True while the whole floor is shown and the traveller has not moved the map since: it refits when cards move. */
    private boolean showingWholeFloor;

    public TerminalMapView(Context context) {
        this(context, null);
    }

    public TerminalMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        background = color(R.color.map_background);
        landsideFill = color(R.color.map_hall_landside);
        airsideFill = color(R.color.map_hall_airside);
        checkpointFill = color(R.color.map_checkpoint);
        userBlue = color(R.color.user_blue);
        pinRed = color(R.color.pin_red);
        pinRedDark = color(R.color.pin_red_dark);

        areaFill.setStyle(Paint.Style.FILL);
        areaEdge.setStyle(Paint.Style.STROKE);
        areaEdge.setStrokeWidth(dp(1));
        areaEdge.setColor(color(R.color.map_area_edge));
        areaShadow.setStyle(Paint.Style.FILL);
        areaShadow.setColor(Color.argb(28, 0, 0, 0));
        for (Paint corridor : new Paint[] {corridorFill, corridorEdge, travelatorFill, routeFill, routeEdge}) {
            corridor.setStyle(Paint.Style.STROKE);
            corridor.setStrokeCap(Paint.Cap.ROUND);
            corridor.setStrokeJoin(Paint.Join.ROUND);
        }
        corridorFill.setColor(color(R.color.map_corridor));
        corridorEdge.setColor(color(R.color.map_corridor_edge));
        travelatorFill.setColor(color(R.color.map_travelator));
        travelatorArrow.setStyle(Paint.Style.STROKE);
        travelatorArrow.setStrokeWidth(dp(2));
        travelatorArrow.setStrokeCap(Paint.Cap.ROUND);
        travelatorArrow.setStrokeJoin(Paint.Join.ROUND);
        travelatorArrow.setColor(color(R.color.map_travelator_arrow));
        routeFill.setColor(color(R.color.route_fill));
        routeFill.setStrokeWidth(dp(7));
        routeEdge.setColor(color(R.color.route_edge));
        routeEdge.setStrokeWidth(dp(10));
        fill.setStyle(Paint.Style.FILL);
        whiteEdge.setStyle(Paint.Style.STROKE);
        whiteEdge.setColor(Color.WHITE);
        whiteEdge.setStrokeWidth(dp(1.5f));
        whiteEdge.setStrokeJoin(Paint.Join.ROUND);
        shadow.setStyle(Paint.Style.FILL);
        shadow.setColor(Color.argb(60, 0, 0, 0));

        int labelColor = color(R.color.map_label);
        int haloColor = color(R.color.map_label_halo);
        labelText.setColor(labelColor);
        labelText.setTextSize(sp(12));
        labelText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        labelHalo.set(labelText);
        labelHalo.setColor(haloColor);
        labelHalo.setStyle(Paint.Style.STROKE);
        labelHalo.setStrokeWidth(dp(3));
        labelHalo.setStrokeJoin(Paint.Join.ROUND);
        areaText.setColor(color(R.color.map_area_label));
        areaText.setTextSize(sp(11));
        areaText.setLetterSpacing(0.05f);
        areaHalo.set(areaText);
        areaHalo.setColor(haloColor);
        areaHalo.setStyle(Paint.Style.STROKE);
        areaHalo.setStrokeWidth(dp(2.5f));
        badgeText.setColor(Color.WHITE);
        badgeText.setTextSize(sp(12));
        badgeText.setTypeface(Typeface.DEFAULT_BOLD);
        chipText.setColor(Color.WHITE);
        chipText.setTextSize(sp(11));
        chipText.setTypeface(Typeface.DEFAULT_BOLD);

        for (NodeType type : NodeType.values()) {
            Drawable icon = AppCompatResources.getDrawable(context, MapStyle.icon(type));
            if (icon != null) {
                icon = DrawableCompat.wrap(icon.mutate());
                DrawableCompat.setTint(icon, Color.WHITE);
                icons.put(type, icon);
            }
            iconColors.put(type, color(MapStyle.color(type)));
        }

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent event) {
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent first, @NonNull MotionEvent event, float dx, float dy) {
                stopFollowingByHand();
                viewport.panBy(-dx, -dy);
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent event) {
                zoomByHand(2f, event.getX(), event.getY());
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent event) {
                MapNode tapped = placeAt(event.getX(), event.getY());
                selected = tapped;
                invalidate();
                performClick();
                if (listener != null) {
                    listener.onPlaceTapped(tapped);
                }
                return true;
            }
        });
        scaler = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                zoomByHand(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    // ---- What to show ----

    /**
     * Shows one floor of the map, with names in {@code language} ("en", "hi", "mr"). With {@code fit}, the whole
     * floor is shown; otherwise the camera stays (e.g. while following the traveller onto another floor).
     */
    public void setFloor(MapJson.MapData mapData, String newFloorId, String newLanguage, boolean fit) {
        boolean newMap = mapData != data;
        data = mapData;
        floorId = newFloorId;
        language = newLanguage;
        floorNodes = mapData.map.nodesOnFloor(newFloorId);
        floorAreas = mapData.areasOnFloor(newFloorId);
        List<MapEdge> edges = new ArrayList<>();
        for (MapEdge edge : mapData.map.edges) {
            MapNode from = mapData.map.node(edge.from);
            MapNode to = mapData.map.node(edge.to);
            if (from != null && to != null && newFloorId.equals(from.floor) && newFloorId.equals(to.floor)) {
                edges.add(edge);
            }
        }
        floorEdges = edges;
        if (selected != null && !newFloorId.equals(selected.floor)) {
            selected = null;
        }
        if (newMap) {
            updateContentBounds();
        }
        if (fit) {
            following = false;
            fitPending = true;
            if (getWidth() > 0) {
                fitFloorNow(false);
            }
        }
        invalidate();
    }

    public String floorId() {
        return floorId;
    }

    /** Selects a place (null = none); with {@code center} the map moves so it is in view. */
    public void select(@Nullable String nodeId, boolean center) {
        selected = data == null || nodeId == null ? null : data.map.node(nodeId);
        if (selected != null && center) {
            animateTo(selected.x, selected.y, Math.max(viewport.scale(), viewport.fitScale() * 2.2f), 0);
        }
        invalidate();
    }

    /** Where the traveller is when not navigating (blue dot), or null when unknown. */
    public void setHere(@Nullable MapNode node, boolean estimate) {
        here = node;
        hereEstimate = estimate;
        invalidate();
    }

    public void setDestination(@Nullable MapNode node) {
        destination = node;
        invalidate();
    }

    /** The route to draw (null = none) and how far along it the traveller is (metres). */
    public void setRoute(@Nullable Route newRoute, double newProgress) {
        route = newRoute;
        progress = newProgress;
        user = newRoute == null || !navigating ? null : newRoute.pointAt(newProgress);
        invalidate();
    }

    /** Turn-by-turn mode: the traveller is a blue arrow, and the map follows and turns with them. */
    public void setNavigating(boolean on) {
        if (navigating == on) {
            return;
        }
        navigating = on;
        user = on && route != null ? route.pointAt(progress) : null;
        if (on) {
            follow();
        } else {
            following = false;
            animating = false;
        }
        invalidate();
    }

    /** Follow the traveller again (the "Re-centre" button). */
    public void follow() {
        if (!navigating || getWidth() == 0) {
            following = navigating;
            return;
        }
        following = true;
        showingWholeFloor = false;
        followScale = getWidth() / FOLLOW_WIDTH_M;
        float free = getHeight() - overlayTop - overlayBottom;
        viewport.setAnchor(getWidth() / 2f, overlayTop + free * FOLLOW_ANCHOR);
        animating = true;
        invalidate();
    }

    public boolean isFollowing() {
        return following;
    }

    /** Parts of the view covered by the search bar / banner (top) and cards (bottom), in pixels. */
    public void setOverlayInsets(float top, float bottom) {
        boolean changed = top != overlayTop || bottom != overlayBottom;
        overlayTop = top;
        overlayBottom = bottom;
        if (changed && showingWholeFloor && !following && getWidth() > 0) {
            fitFloorNow(false);
        }
        if (following) {
            float free = getHeight() - overlayTop - overlayBottom;
            viewport.setAnchor(getWidth() / 2f, overlayTop + free * FOLLOW_ANCHOR);
            invalidate();
        }
    }

    /** Shows the whole floor again, north up. */
    public void fitFloor() {
        following = false;
        fitFloorNow(true);
    }

    /** Shows the whole route (its part on this floor), north up, between the overlays. */
    public void fitRoute() {
        if (route == null || getWidth() == 0) {
            return;
        }
        following = false;
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        boolean any = false;
        for (MapNode node : route.nodes) {
            if (floorId.equals(node.floor)) {
                minX = Math.min(minX, node.x);
                minY = Math.min(minY, node.y);
                maxX = Math.max(maxX, node.x);
                maxY = Math.max(maxY, node.y);
                any = true;
            }
        }
        if (!any) {
            fitFloorNow(true);
            return;
        }
        animateToBox(minX - 10, minY - 10, maxX + 10, maxY + 10, dp(44));
    }

    /** Moves the map so this place is in the middle, a little zoomed in, north up. */
    public void showPlace(MapNode node) {
        following = false;
        animateTo(node.x, node.y, Math.max(viewport.scale(), viewport.fitScale() * 2.5f), 0);
    }

    // ---- Camera ----

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        viewport.setViewSize(width, height, dp(24));
        updateContentBounds();
        if (fitPending || oldWidth == 0) {
            fitFloorNow(false);
        }
        if (navigating) {
            follow();
        }
    }

    private void fitFloorNow(boolean animate) {
        if (getWidth() == 0) {
            fitPending = true;
            return;
        }
        fitPending = false;
        showingWholeFloor = true;
        double[] box = floorBox();
        if (box == null) {
            viewport.fit(overlayTop, getHeight() - overlayBottom);
            invalidate();
            return;
        }
        if (animate) {
            animateToBox(box[0], box[1], box[2], box[3], 0);
            showingWholeFloor = true;
        } else {
            viewport.fitBounds(box[0], box[1], box[2], box[3], overlayTop, getHeight() - overlayBottom);
            animating = false;
            invalidate();
        }
    }

    /** Animates to the camera that would show this box between the overlays ({@code extraTop}: room for a pin). */
    private void animateToBox(double minX, double minY, double maxX, double maxY, float extraTop) {
        showingWholeFloor = false;
        MapViewport goal = new MapViewport();
        goal.setViewSize(getWidth(), getHeight(), dp(24));
        double[] all = allFloorsBox();
        if (all != null) {
            goal.setContent(all[0], all[1], all[2], all[3]);
        }
        goal.fit();
        goal.fitBounds(minX, minY, maxX, maxY, overlayTop + extraTop, getHeight() - overlayBottom);
        viewport.setAnchor(goal.anchorX(), goal.anchorY());
        animateTo(goal.centerX(), goal.centerY(), goal.scale(), 0);
    }

    private void animateTo(double x, double y, float scale, float bearing) {
        showingWholeFloor = false;
        targetX = x;
        targetY = y;
        targetScale = scale;
        targetBearing = bearing;
        animating = true;
        invalidate();
    }

    /** One frame of the camera moving smoothly towards its target (or following the traveller). */
    private void stepCamera() {
        if (following && user != null) {
            targetX = user.x;
            targetY = user.y;
            targetScale = followScale;
            targetBearing = (float) user.bearing;
            animating = true;
        }
        if (!animating) {
            return;
        }
        double dx = targetX - viewport.centerX();
        double dy = targetY - viewport.centerY();
        float dScale = targetScale - viewport.scale();
        float dBearing = (float) NavSession.signedDifference(viewport.bearing(), targetBearing);
        boolean settled = Math.hypot(dx, dy) < 0.05 && Math.abs(dScale) < viewport.scale() * 0.005
                && Math.abs(dBearing) < 0.3f;
        if (settled) {
            viewport.setCamera(targetX, targetY, targetScale, targetBearing);
            if (!following) {
                animating = false;
            }
            return;
        }
        viewport.setCamera(viewport.centerX() + dx * 0.22, viewport.centerY() + dy * 0.22,
                viewport.scale() + dScale * 0.2f, viewport.bearing() + dBearing * 0.15f);
        postInvalidateOnAnimation();
    }

    private void stopFollowingByHand() {
        animating = false;
        showingWholeFloor = false;
        if (following) {
            following = false;
            if (listener != null) {
                listener.onFollowStopped();
            }
        }
    }

    private void zoomByHand(float factor, float focusX, float focusY) {
        showingWholeFloor = false;
        if (following) {
            // Zooming keeps following: the new zoom is kept for the rest of the walk.
            followScale = Math.max(viewport.fitScale() * MapViewport.MIN_ZOOM,
                    Math.min(viewport.fitScale() * MapViewport.MAX_ZOOM, followScale * factor));
            viewport.zoomBy(factor, viewport.anchorX(), viewport.anchorY());
        } else {
            animating = false;
            viewport.zoomBy(factor, focusX, focusY);
        }
        invalidate();
    }

    /** The zoom limits come from the whole terminal (both floors), so they stay the same on every floor. */
    private void updateContentBounds() {
        double[] box = allFloorsBox();
        if (box == null) {
            viewport.setContent(0, 0, 1, 1);
        } else {
            viewport.setContent(box[0], box[1], box[2], box[3]);
        }
        viewport.setViewSize(Math.max(1, getWidth()), Math.max(1, getHeight()), dp(24));
        float scale = viewport.scale();
        double x = viewport.centerX();
        double y = viewport.centerY();
        float bearing = viewport.bearing();
        viewport.fit(); // works out the whole-terminal scale the zoom limits are relative to
        viewport.setCamera(x, y, scale, bearing);
    }

    @Nullable
    private double[] floorBox() {
        return box(floorAreas, floorNodes);
    }

    @Nullable
    private double[] allFloorsBox() {
        return data == null ? null : box(data.areas, data.map.nodes);
    }

    @Nullable
    private static double[] box(List<MapArea> areas, List<MapNode> nodes) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (MapArea area : areas) {
            for (double[] point : area.points) {
                minX = Math.min(minX, point[0]);
                maxX = Math.max(maxX, point[0]);
                minY = Math.min(minY, point[1]);
                maxY = Math.max(maxY, point[1]);
            }
        }
        for (MapNode node : nodes) {
            minX = Math.min(minX, node.x);
            maxX = Math.max(maxX, node.x);
            minY = Math.min(minY, node.y);
            maxY = Math.max(maxY, node.y);
        }
        return minX > maxX ? null : new double[] {minX, minY, maxX, maxY};
    }

    // ---- Drawing ----

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        stepCamera();
        canvas.drawColor(background);
        if (data == null) {
            return;
        }
        for (MapArea area : floorAreas) {
            drawArea(canvas, area);
        }
        drawCorridors(canvas);
        drawRoute(canvas);
        List<RectF> taken = new ArrayList<>();
        drawMarkers(canvas, taken);
        drawFloorChanges(canvas, taken);
        if (destination != null && floorId.equals(destination.floor)) {
            drawPin(canvas, destination, taken);
        }
        if (selected != null && selected != destination && floorId.equals(selected.floor)) {
            drawPin(canvas, selected, taken);
        }
        drawTraveller(canvas, taken);
        drawLabels(canvas, taken);
    }

    private float sx(double x, double y) {
        return viewport.toScreenX(x, y);
    }

    private float sy(double x, double y) {
        return viewport.toScreenY(x, y);
    }

    private void drawArea(Canvas canvas, MapArea area) {
        Path path = new Path();
        for (int i = 0; i < area.points.size(); i++) {
            double[] point = area.points.get(i);
            float x = sx(point[0], point[1]);
            float y = sy(point[0], point[1]);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        canvas.save();
        canvas.translate(0, dp(2));
        canvas.drawPath(path, areaShadow);
        canvas.restore();
        areaFill.setColor(areaColor(area.kind));
        canvas.drawPath(path, areaFill);
        canvas.drawPath(path, areaEdge);
    }

    /** Walkways like roads: all grey edges first, then all white middles, so crossings join cleanly. */
    private void drawCorridors(Canvas canvas) {
        float width = clamp(CORRIDOR_WIDTH_M * viewport.scale(), dp(5), dp(26));
        corridorEdge.setStrokeWidth(width + dp(2.5f));
        corridorFill.setStrokeWidth(width);
        travelatorFill.setStrokeWidth(width);
        for (MapEdge edge : floorEdges) {
            drawEdgeLine(canvas, edge, corridorEdge);
        }
        for (MapEdge edge : floorEdges) {
            drawEdgeLine(canvas, edge, edge.kind == EdgeKind.TRAVELATOR ? travelatorFill : corridorFill);
        }
        for (MapEdge edge : floorEdges) {
            if (edge.kind == EdgeKind.TRAVELATOR) {
                drawTravelatorArrows(canvas, edge, width);
            }
        }
    }

    private void drawEdgeLine(Canvas canvas, MapEdge edge, Paint paint) {
        MapNode from = data.map.node(edge.from);
        MapNode to = data.map.node(edge.to);
        canvas.drawLine(sx(from.x, from.y), sy(from.x, from.y), sx(to.x, to.y), sy(to.x, to.y), paint);
    }

    /** Small chevrons along a moving walkway, pointing the way it moves. */
    private void drawTravelatorArrows(Canvas canvas, MapEdge edge, float width) {
        MapNode from = data.map.node(edge.from);
        MapNode to = data.map.node(edge.to);
        float x1 = sx(from.x, from.y);
        float y1 = sy(from.x, from.y);
        float x2 = sx(to.x, to.y);
        float y2 = sy(to.x, to.y);
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (length < dp(30)) {
            return;
        }
        float ux = (x2 - x1) / length;
        float uy = (y2 - y1) / length;
        float size = Math.min(width * 0.35f, dp(5));
        for (float along = dp(16); along < length - dp(10); along += dp(22)) {
            float cx = x1 + ux * along;
            float cy = y1 + uy * along;
            Path chevron = new Path();
            chevron.moveTo(cx - ux * size - uy * size, cy - uy * size + ux * size);
            chevron.lineTo(cx, cy);
            chevron.lineTo(cx - ux * size + uy * size, cy - uy * size - ux * size);
            canvas.drawPath(chevron, travelatorArrow);
        }
    }

    /** The part of the route still to walk on this floor (all of it in the preview). */
    private void drawRoute(Canvas canvas) {
        if (route == null || route.legs.isEmpty()) {
            return;
        }
        int first = navigating ? Math.max(0, route.legIndexAt(progress)) : 0;
        Path path = new Path();
        boolean open = false;
        for (int i = first; i < route.legs.size(); i++) {
            RouteLeg leg = route.legs.get(i);
            if (leg.changesFloor() || !floorId.equals(leg.from.floor)) {
                open = false;
                continue;
            }
            double fromX = leg.from.x;
            double fromY = leg.from.y;
            if (navigating && i == first && user != null) {
                fromX = user.x;
                fromY = user.y;
            }
            if (!open) {
                path.moveTo(sx(fromX, fromY), sy(fromX, fromY));
                open = true;
            }
            path.lineTo(sx(leg.to.x, leg.to.y), sy(leg.to.x, leg.to.y));
        }
        canvas.drawPath(path, routeEdge);
        canvas.drawPath(path, routeFill);
    }

    /** "↓ Arrivals" chips where the route takes a lift, escalator or stairs from this floor. */
    private void drawFloorChanges(Canvas canvas, List<RectF> taken) {
        if (route == null) {
            return;
        }
        int first = navigating ? Math.max(0, route.legIndexAt(progress)) : 0;
        for (int i = first; i < route.legs.size(); i++) {
            RouteLeg leg = route.legs.get(i);
            if (!leg.changesFloor() || !floorId.equals(leg.from.floor)) {
                continue;
            }
            MapFloor fromFloor = data.map.floor(leg.from.floor);
            MapFloor toFloor = data.map.floor(leg.to.floor);
            boolean up = fromFloor != null && toFloor != null && toFloor.order > fromFloor.order;
            String text = (up ? "↑ " : "↓ ") + (toFloor == null ? leg.to.floor : toFloor.name(language));
            float x = sx(leg.from.x, leg.from.y);
            float y = sy(leg.from.x, leg.from.y) - dp(26);
            float width = chipText.measureText(text) + dp(14);
            RectF chip = new RectF(x - width / 2, y - dp(11), x + width / 2, y + dp(11));
            fill.setColor(Color.argb(235, 32, 33, 36));
            canvas.drawRoundRect(chip, dp(11), dp(11), fill);
            canvas.drawText(text, chip.left + dp(7), chip.centerY() - (chipText.ascent() + chipText.descent()) / 2,
                    chipText);
            taken.add(chip);
        }
    }

    private void drawMarkers(Canvas canvas, List<RectF> taken) {
        boolean small = viewport.zoom() < 1.6f;
        float radius = small ? dp(9) : dp(11);
        for (MapNode node : floorNodes) {
            float x = sx(node.x, node.y);
            float y = sy(node.x, node.y);
            if (x < -dp(40) || y < -dp(40) || x > getWidth() + dp(40) || y > getHeight() + dp(40)) {
                continue;
            }
            int color = iconColors.get(node.type);
            if (node.type == NodeType.GATE) {
                String text = node.ref == null ? "?" : node.ref;
                float width = Math.max(badgeText.measureText(text) + dp(12), dp(26));
                RectF badge = new RectF(x - width / 2, y - dp(10), x + width / 2, y + dp(10));
                canvas.drawRoundRect(offset(badge, dp(1)), dp(6), dp(6), shadow);
                fill.setColor(color);
                canvas.drawRoundRect(badge, dp(6), dp(6), fill);
                canvas.drawRoundRect(badge, dp(6), dp(6), whiteEdge);
                canvas.drawText(text, x - badgeText.measureText(text) / 2,
                        y - (badgeText.ascent() + badgeText.descent()) / 2, badgeText);
                taken.add(badge);
                continue;
            }
            canvas.drawCircle(x, y + dp(1), radius, shadow);
            fill.setColor(color);
            canvas.drawCircle(x, y, radius, fill);
            canvas.drawCircle(x, y, radius, whiteEdge);
            Drawable icon = icons.get(node.type);
            if (icon != null) {
                int half = Math.round(radius * 0.62f);
                icon.setBounds(Math.round(x) - half, Math.round(y) - half, Math.round(x) + half, Math.round(y) + half);
                icon.draw(canvas);
            }
            taken.add(new RectF(x - radius, y - radius, x + radius, y + radius));
        }
    }

    /** A red map pin whose point touches the place. */
    private void drawPin(Canvas canvas, MapNode node, List<RectF> taken) {
        float x = sx(node.x, node.y);
        float y = sy(node.x, node.y) - (node.type == NodeType.GATE ? dp(8) : dp(4));
        float r = dp(12);
        float cy = y - dp(26);
        Path pin = new Path();
        pin.moveTo(x, y);
        pin.cubicTo(x - r * 0.35f, y - r * 0.9f, x - r, cy + r * 0.8f, x - r, cy);
        pin.arcTo(new RectF(x - r, cy - r, x + r, cy + r), 180, 180, false);
        pin.cubicTo(x + r, cy + r * 0.8f, x + r * 0.35f, y - r * 0.9f, x, y);
        pin.close();
        canvas.drawOval(new RectF(x - dp(5), y - dp(2), x + dp(5), y + dp(2)), shadow);
        fill.setColor(pinRed);
        canvas.drawPath(pin, fill);
        Paint edge = new Paint(whiteEdge);
        edge.setColor(pinRedDark);
        edge.setStrokeWidth(dp(1));
        canvas.drawPath(pin, edge);
        fill.setColor(pinRedDark);
        canvas.drawCircle(x, cy, dp(4.5f), fill);
        taken.add(new RectF(x - r, cy - r, x + r, y));
    }

    /** Blue arrow while navigating (pointing the way to walk), else a blue dot at the traveller's place. */
    private void drawTraveller(Canvas canvas, List<RectF> taken) {
        if (navigating && user != null) {
            if (!floorId.equals(user.floor)) {
                return;
            }
            float x = sx(user.x, user.y);
            float y = sy(user.x, user.y);
            fill.setColor(ColorUtils.setAlphaComponent(userBlue, 40));
            canvas.drawCircle(x, y, dp(26), fill);
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate((float) (user.bearing - viewport.bearing()));
            float s = dp(15);
            Path arrow = new Path();
            arrow.moveTo(0, -s);
            arrow.lineTo(s * 0.78f, s * 0.82f);
            arrow.lineTo(0, s * 0.42f);
            arrow.lineTo(-s * 0.78f, s * 0.82f);
            arrow.close();
            canvas.save();
            canvas.translate(0, dp(1.5f));
            canvas.drawPath(arrow, shadow);
            canvas.restore();
            Paint edge = new Paint(whiteEdge);
            edge.setStrokeWidth(dp(3));
            canvas.drawPath(arrow, edge);
            fill.setColor(userBlue);
            canvas.drawPath(arrow, fill);
            canvas.restore();
            taken.add(new RectF(x - s, y - s, x + s, y + s));
            return;
        }
        if (here == null || !floorId.equals(here.floor)) {
            return;
        }
        float x = sx(here.x, here.y);
        float y = sy(here.x, here.y);
        // The pale circle shows how sure the position is: bigger when it is an estimate.
        fill.setColor(ColorUtils.setAlphaComponent(userBlue, hereEstimate ? 45 : 35));
        canvas.drawCircle(x, y, hereEstimate ? dp(30) : dp(18), fill);
        canvas.drawCircle(x, y + dp(1), dp(10), shadow);
        fill.setColor(Color.WHITE);
        canvas.drawCircle(x, y, dp(10), fill);
        fill.setColor(userBlue);
        canvas.drawCircle(x, y, dp(7), fill);
        taken.add(new RectF(x - dp(10), y - dp(10), x + dp(10), y + dp(10)));
    }

    /** Names with a halo, most important first; a name that would overlap something already drawn is left out. */
    private void drawLabels(Canvas canvas, List<RectF> taken) {
        List<MapNode> ordered = new ArrayList<>();
        for (MapNode node : floorNodes) {
            if (node.type != NodeType.GATE || node == selected || node == destination) {
                ordered.add(node);
            }
        }
        final List<MapNode> ahead = route == null ? Collections.<MapNode>emptyList() : route.remainingNodes(progress);
        Collections.sort(ordered, (a, b) -> Integer.compare(labelRank(a, ahead), labelRank(b, ahead)));
        float radius = viewport.zoom() < 1.6f ? dp(9) : dp(11);
        List<RectF> placed = new ArrayList<>();
        for (MapNode node : ordered) {
            String text = node.name(language);
            float width = labelText.measureText(text);
            float height = labelText.getTextSize();
            float x = sx(node.x, node.y);
            float y = sy(node.x, node.y);
            float gap = (node.type == NodeType.GATE ? dp(14) : radius) + dp(4);
            RectF[] candidates = {
                    new RectF(x + gap, y - height / 2, x + gap + width, y + height / 2),
                    new RectF(x - gap - width, y - height / 2, x - gap, y + height / 2),
                    new RectF(x - width / 2, y + gap, x + width / 2, y + gap + height),
                    new RectF(x - width / 2, y - gap - height, x + width / 2, y - gap)};
            for (RectF rect : candidates) {
                if (fitsOnScreen(rect) && !overlaps(rect, placed) && !overlapsOthers(rect, taken, x, y, gap)) {
                    float baseline = rect.bottom - labelText.descent() / 2;
                    canvas.drawText(text, rect.left, baseline, labelHalo);
                    canvas.drawText(text, rect.left, baseline, labelText);
                    placed.add(rect);
                    break;
                }
            }
        }
        // Hall and pier names last, only where there is room.
        for (MapArea area : floorAreas) {
            double cx = 0;
            double cy = 0;
            int count = Math.max(1, area.points.size() - 1); // the ring repeats its first point at the end
            for (int i = 0; i < count; i++) {
                cx += area.points.get(i)[0];
                cy += area.points.get(i)[1];
            }
            String text = area.name(language).toUpperCase(java.util.Locale.ROOT);
            float width = areaText.measureText(text);
            float x = sx(cx / count, cy / count);
            float y = sy(cx / count, cy / count);
            RectF rect = new RectF(x - width / 2, y - areaText.getTextSize() / 2, x + width / 2,
                    y + areaText.getTextSize() / 2);
            if (fitsOnScreen(rect) && !overlaps(rect, placed) && !overlaps(rect, taken)) {
                canvas.drawText(text, rect.left, rect.bottom - areaText.descent() / 2, areaHalo);
                canvas.drawText(text, rect.left, rect.bottom - areaText.descent() / 2, areaText);
                placed.add(rect);
            }
        }
    }

    /** Lower = drawn first: the selected place and destination, then places still ahead on the route, then by kind. */
    private int labelRank(MapNode node, List<MapNode> ahead) {
        if (node == selected || node == destination) {
            return -2;
        }
        if (ahead.contains(node)) {
            return -1;
        }
        return MapStyle.labelPriority(node.type);
    }

    private boolean fitsOnScreen(RectF rect) {
        return rect.left >= 0 && rect.top >= overlayTop && rect.right <= getWidth()
                && rect.bottom <= getHeight() - overlayBottom;
    }

    private static boolean overlaps(RectF rect, List<RectF> others) {
        for (RectF other : others) {
            if (RectF.intersects(rect, other)) {
                return true;
            }
        }
        return false;
    }

    /** Overlaps a marker other than the label's own (its own sits at (x, y), inside the gap). */
    private static boolean overlapsOthers(RectF rect, List<RectF> markers, float x, float y, float gap) {
        for (RectF marker : markers) {
            boolean own = Math.abs(marker.centerX() - x) < 1 && Math.abs(marker.centerY() - y) < 1;
            if (!own && RectF.intersects(rect, marker)) {
                return true;
            }
        }
        return false;
    }

    private int areaColor(String kind) {
        switch (kind) {
            case MapArea.KIND_AIRSIDE:
            case MapArea.KIND_PIER:
                return airsideFill;
            case MapArea.KIND_CHECKPOINT:
                return checkpointFill;
            case MapArea.KIND_LANDSIDE:
            default:
                return landsideFill;
        }
    }

    // ---- Touch ----

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true); // the map handles its own drags
        }
        boolean handled = scaler.onTouchEvent(event);
        handled = gestures.onTouchEvent(event) || handled;
        return handled || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** The place nearest to a screen point, within a finger's reach (28dp); null when there is none. */
    @Nullable
    private MapNode placeAt(float screenX, float screenY) {
        MapNode best = null;
        double bestDistance = dp(28);
        for (MapNode node : floorNodes) {
            double distance = Math.hypot(sx(node.x, node.y) - screenX, sy(node.x, node.y) - screenY);
            if (distance <= bestDistance) {
                best = node;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ---- Helpers ----

    private int color(@ColorRes int id) {
        return ContextCompat.getColor(getContext(), id);
    }

    private static RectF offset(RectF rect, float dy) {
        RectF moved = new RectF(rect);
        moved.offset(0, dy);
        return moved;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
