package com.safarsaathi.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Small web settings.
 *
 * <p>Spring Boot already serves every file in {@code src/main/resources/static/} as-is, so the front-desk page
 * is at {@code /desk/index.html} and the demo page at {@code /demo/index.html}. Here we also make the short
 * addresses {@code /desk}, {@code /desk/}, {@code /demo} and {@code /demo/} show them.
 * "forward:" means the server hands the request to that file internally; the address in the browser does not
 * change. (Spring Boot 3 treats "/desk" and "/desk/" as different addresses, so both are listed.)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/desk").setViewName("forward:/desk/index.html");
        registry.addViewController("/desk/").setViewName("forward:/desk/index.html");
        // The demo control page for the judges' laptop (Phase 2).
        registry.addViewController("/demo").setViewName("forward:/demo/index.html");
        registry.addViewController("/demo/").setViewName("forward:/demo/index.html");
    }
}
