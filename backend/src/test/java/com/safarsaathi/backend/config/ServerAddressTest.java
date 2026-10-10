package com.safarsaathi.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/**
 * Which network address the backend listens on (server.address in application.yml).
 *
 * <p>Reads application.yml itself instead of starting Spring, so a teammate's .env or environment variables can
 * never change the answer: the test checks the file's own default and that SERVER_ADDRESS overrides it.
 */
class ServerAddressTest {

    @Test
    void listensOnThisComputerOnlyByDefault() throws IOException {
        assertThat(serverAddress(Map.of())).isEqualTo("127.0.0.1");
    }

    @Test
    void serverAddressVariableOpensItUpForDockerAndHosting() throws IOException {
        assertThat(serverAddress(Map.of("SERVER_ADDRESS", "0.0.0.0"))).isEqualTo("0.0.0.0");
    }

    @Test
    void onlyLoopbackAddressesCountAsThisComputerOnly() {
        assertThat(ProviderStatusLogger.isThisComputerOnly("127.0.0.1")).isTrue();
        assertThat(ProviderStatusLogger.isThisComputerOnly("localhost")).isTrue();
        assertThat(ProviderStatusLogger.isThisComputerOnly("0.0.0.0")).isFalse();
        assertThat(ProviderStatusLogger.isThisComputerOnly(null)).isFalse(); // no address = every network
    }

    /** server.address as Spring would see it, with only application.yml and the given variables. */
    private static String serverAddress(Map<String, Object> variables) throws IOException {
        List<PropertySource<?>> yaml =
                new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("variables", variables)); // variables win over the file, like in Spring
        yaml.forEach(sources::addLast);
        return new PropertySourcesPropertyResolver(sources).getProperty("server.address");
    }
}
