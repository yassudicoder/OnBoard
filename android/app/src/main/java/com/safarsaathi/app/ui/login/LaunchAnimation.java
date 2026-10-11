package com.safarsaathi.app.ui.login;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import com.safarsaathi.app.R;

/**
 * The animated OnBoard launch screen, played once per cold start over MainActivity. It follows the team's Figma frames
 * "Loading 1-3" (static frames; the timing is ours, about 2.5 s):
 * <ol>
 *   <li>from the system launch screen (big "A" mark in the middle) to frame 1 "Ready": the plane, small and low, with
 *       the start of a contrail;</li>
 *   <li>frame 2 "Takeoff": the plane flies straight up, drawing a long contrail behind it;</li>
 *   <li>frame 3 "Logo": plane and trail fade, the "onboard" badge appears in the middle with "From your door to your
 *       destination.", then the progress bar fills ("Getting your trip ready…");</li>
 *   <li>everything fades into the app.</li>
 * </ol>
 * Positions are fractions of the screen height, measured from the 852 dp tall Figma frames. Phones with animations
 * switched off (accessibility setting) go straight to the app.
 */
public final class LaunchAnimation {

    // Figma "Loading" frames, as fractions of the frame height (852 dp).
    private static final float PLANE_READY_Y = 656f / 852f;
    private static final float PLANE_HIGH_Y = 239f / 852f;
    private static final float TRAIL_TOP_Y = 291f / 852f;
    private static final float TRAIL_BOTTOM_Y = 709f / 852f;
    private static final float LOGO_Y = 412f / 852f;
    private static final float TAGLINE_Y = 487f / 852f;
    private static final float PROGRESS_Y = 760f / 852f;
    /** The plane is ~55 dp wide in the Figma; the mark fills ~50/108 of its 288 dp drawable. */
    private static final float PLANE_SCALE = 55f / (288f * 50f / 108f);

    private LaunchAnimation() {
    }

    /** Lays the launch screen over the activity, animates it, removes it and then runs {@code onDone}. */
    public static void play(Activity activity, Runnable onDone) {
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null || animationsOff(activity)) {
            onDone.run();
            return;
        }
        View overlay = LayoutInflater.from(activity).inflate(R.layout.view_launch_animation, root, false);
        root.addView(overlay);
        View mark = overlay.findViewById(R.id.launch_mark);
        View trail = overlay.findViewById(R.id.launch_trail);
        View logoBottom = overlay.findViewById(R.id.launch_logo_bottom);
        View logo = overlay.findViewById(R.id.launch_logo);
        View tagline = overlay.findViewById(R.id.launch_tagline);
        View progress = overlay.findViewById(R.id.launch_progress);
        View progressFill = overlay.findViewById(R.id.launch_progress_fill);

        // Wait for the first layout: every position depends on the screen height.
        overlay.post(() -> {
            float height = overlay.getHeight();
            float middle = height / 2f;

            // The contrail: a 2 dp line from TRAIL_TOP to TRAIL_BOTTOM that grows upwards from its bottom end.
            ViewGroup.LayoutParams trailParams = trail.getLayoutParams();
            trailParams.height = Math.round((TRAIL_BOTTOM_Y - TRAIL_TOP_Y) * height);
            trail.setLayoutParams(trailParams);
            trail.setTranslationY(TRAIL_TOP_Y * height);
            trail.setPivotY(trailParams.height);
            trail.setScaleY(0.05f);

            logo.setTranslationY(LOGO_Y * height - middle);
            tagline.setTranslationY(TAGLINE_Y * height - middle);
            progress.setTranslationY(PROGRESS_Y * height - middle);
            progressFill.setPivotX(0);
            progressFill.setScaleX(0f);

            // 1. System launch screen -> "Ready": the plane shrinks and drops low; the bottom logo fades.
            AnimatorSet ready = together(400, new DecelerateInterpolator(),
                    ObjectAnimator.ofFloat(mark, View.SCALE_X, 1f, PLANE_SCALE),
                    ObjectAnimator.ofFloat(mark, View.SCALE_Y, 1f, PLANE_SCALE),
                    ObjectAnimator.ofFloat(mark, View.TRANSLATION_Y, 0, PLANE_READY_Y * height - middle),
                    ObjectAnimator.ofFloat(logoBottom, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(trail, View.ALPHA, 0f, 1f));

            // 2. "Takeoff": straight up, the contrail growing behind it.
            AnimatorSet takeOff = together(650, new AccelerateDecelerateInterpolator(),
                    ObjectAnimator.ofFloat(mark, View.TRANSLATION_Y, PLANE_READY_Y * height - middle,
                            PLANE_HIGH_Y * height - middle),
                    ObjectAnimator.ofFloat(trail, View.SCALE_Y, 0.05f, 1f));
            takeOff.setStartDelay(150);

            // 3. "Logo": plane and trail fade, the badge pops in with the tagline.
            AnimatorSet logoIn = together(350, new OvershootInterpolator(1.2f),
                    ObjectAnimator.ofFloat(mark, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(trail, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.85f, 1f),
                    ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.85f, 1f),
                    ObjectAnimator.ofFloat(tagline, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(progress, View.ALPHA, 0f, 1f));

            ObjectAnimator fill = ObjectAnimator.ofFloat(progressFill, View.SCALE_X, 0f, 1f);
            fill.setDuration(650);
            fill.setInterpolator(new DecelerateInterpolator());

            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f, 0f);
            fadeOut.setDuration(300);
            fadeOut.setStartDelay(150);
            fadeOut.setInterpolator(new AccelerateInterpolator());

            AnimatorSet all = new AnimatorSet();
            all.playSequentially(ready, takeOff, logoIn, fill, fadeOut);
            all.setStartDelay(120);
            all.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    root.removeView(overlay);
                    onDone.run();
                }
            });
            all.start();
        });
    }

    private static AnimatorSet together(long duration, android.animation.TimeInterpolator interpolator,
                                        Animator... animators) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(animators);
        set.setDuration(duration);
        set.setInterpolator(interpolator);
        return set;
    }

    private static boolean animationsOff(Activity activity) {
        float scale = Settings.Global.getFloat(activity.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return scale == 0f;
    }
}
