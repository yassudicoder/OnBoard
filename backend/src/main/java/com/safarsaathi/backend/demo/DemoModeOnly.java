package com.safarsaathi.backend.demo;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import com.safarsaathi.backend.config.AppProperties;

/**
 * Spring creates a class marked {@code @Conditional(DemoModeOnly.class)} only in demo mode (APP_MODE=demo, the
 * default). Used on {@link DemoController} and {@link DemoService}.
 *
 * <p>Why: the demo controls can delay, cancel and reset every trip, and they have no login. In prod mode
 * (APP_MODE=prod, e.g. on Render) they must not exist at all: POST /api/demo/... then simply answers 404.
 * The health and trip APIs are not affected.
 *
 * <p>It reads the mode the same way {@link AppProperties} does ({@link AppProperties#normaliseMode}), so "Demo" and
 * "demo " count as demo mode here too. A mode that is neither demo nor prod stops the app at startup anyway.
 */
public final class DemoModeOnly implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = context.getEnvironment().getProperty("safarsaathi.mode");
        return AppProperties.DEFAULT_MODE.equals(AppProperties.normaliseMode(mode));
    }
}
