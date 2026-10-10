package com.safarsaathi.app.data.ask;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * PRIVACY GUARD (user decision 2026-10-11): Ask may only see a document's type, holder first name, dates, countries,
 * status and sample flag. If someone adds a field here (a number, an MRZ line, an image, a path, a full name, a birth
 * date), this test fails, so the change has to be a deliberate, reviewed decision.
 */
public class DocumentFactsTest {

    @Test
    public void documentSummary_hasOnlyTheAllowedFields() {
        Set<String> allowed = new TreeSet<>(Arrays.asList("type", "holderFirstName", "expiry", "coveredFrom",
                "coveredTo", "countries", "status", "sample"));
        assertEquals(allowed, instanceFields(DocumentFacts.Doc.class));
    }

    @Test
    public void documentFacts_holdOnlyAccessTitleAndSummaries() {
        assertEquals(new TreeSet<>(Arrays.asList("access", "tripTitle", "docs")), instanceFields(DocumentFacts.class));
    }

    private static Set<String> instanceFields(Class<?> type) {
        Set<String> names = new TreeSet<>();
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                names.add(field.getName());
            }
        }
        return names;
    }
}
