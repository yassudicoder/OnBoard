package com.safarsaathi.app.ui.navigate;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.annotation.Nullable;

/**
 * The phone's own motion sensors, for moving the blue arrow along the route without GPS (which does not work well
 * inside a terminal):
 * <ul>
 *   <li><b>Step detector:</b> one call per step the phone feels (needs the "physical activity" permission on Android
 *       10+). Each step moves the traveller one step length along the route (an estimate).</li>
 *   <li><b>Game rotation vector:</b> which way the phone points. It uses no compass, so the steel of the terminal
 *       does not disturb it; it has no true north either, which is fine: only TURNS (changes of direction) are used,
 *       see {@link NavSession#onHeading}.</li>
 * </ul>
 * Phones (or emulators) without these sensors simply never call back; the traveller then updates their place with
 * the QR codes or "I'm here".
 */
public final class MotionSensors implements SensorEventListener {

    public interface Listener {
        void onStep();

        /** Direction the phone points, degrees clockwise (0-360) from the sensor's own reference. */
        void onHeading(double degrees);
    }

    private final SensorManager manager;
    @Nullable
    private final Sensor stepDetector;
    @Nullable
    private final Sensor rotation;
    private final float[] matrix = new float[9];
    private final float[] orientation = new float[3];
    @Nullable
    private Listener listener;
    private long lastHeadingAt;

    public MotionSensors(Context context) {
        manager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        stepDetector = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        Sensor game = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        rotation = game != null || manager == null ? game : manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    /** True when the phone can count steps. */
    public boolean canCountSteps() {
        return stepDetector != null;
    }

    /** Starts listening ({@code withSteps}: only after the physical-activity permission was granted). */
    public void start(Listener newListener, boolean withSteps) {
        stop();
        listener = newListener;
        if (manager == null) {
            return;
        }
        if (withSteps && stepDetector != null) {
            manager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI);
        }
        if (rotation != null) {
            manager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void stop() {
        if (manager != null) {
            manager.unregisterListener(this);
        }
        listener = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (listener == null) {
            return;
        }
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            listener.onStep();
            return;
        }
        long now = event.timestamp;
        if (now - lastHeadingAt < 150_000_000L) { // at most ~7 headings a second
            return;
        }
        lastHeadingAt = now;
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        SensorManager.getOrientation(matrix, orientation);
        double degrees = Math.toDegrees(orientation[0]);
        listener.onHeading(degrees < 0 ? degrees + 360 : degrees);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing to do: only changes of direction are used.
    }
}
