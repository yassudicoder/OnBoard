package com.safarsaathi.backend.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads one JSON file from the repo-root mock-data folder (Maven copies it into the jar under {@code mock-data/}, see
 * pom.xml), strictly: a misspelt field name stops the app with an error that names it, instead of leaving a value
 * empty. Used by the providers that load reference and SAMPLE data at startup.
 */
public final class MockDataFile {

    private MockDataFile() {
    }

    /**
     * @param path e.g. "mock-data/recovery/airline-policies.json"
     * @throws IllegalStateException when the file is missing
     * @throws UncheckedIOException  when it is not valid JSON of that shape
     */
    public static <T> T read(ObjectMapper objectMapper, String path, Class<T> type) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("classpath:" + path + " not found. Maven copies it from the repo-root "
                    + "mock-data folder (see pom.xml): rebuild with ./mvnw clean package");
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path + ": " + e.getMessage(), e);
        }
    }

    /** Stops the app when a file breaks its rules: "{path} breaks the rules for sourced data: a; b". */
    public static void requireNoProblems(String path, List<String> problems) {
        if (!problems.isEmpty()) {
            throw new IllegalStateException(path + " breaks the rules for sourced data: " + String.join("; ", problems));
        }
    }
}
