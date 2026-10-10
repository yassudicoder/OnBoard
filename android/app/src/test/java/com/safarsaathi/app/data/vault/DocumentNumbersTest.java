package com.safarsaathi.app.data.vault;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Unit tests for how document numbers are cleaned up and hidden ({@link DocumentNumbers}). */
public class DocumentNumbersTest {

    @Test
    public void normalise_capitalsWithoutSpaces() {
        assertEquals("S0000001", DocumentNumbers.normalise(" s 000 0001 "));
        assertNull(DocumentNumbers.normalise("   "));
        assertNull(DocumentNumbers.normalise(null));
    }

    @Test
    public void mask_showsOnlyTheLastTwoCharacters() {
        assertEquals("••••••01", DocumentNumbers.mask("S0000001"));
        assertEquals("••••••••01", DocumentNumbers.mask("POL0000001"));
        assertEquals("01", DocumentNumbers.visibleEnd("S0000001"));
    }

    @Test
    public void mask_hidesShortNumbersCompletely() {
        assertEquals("••••", DocumentNumbers.mask("1234"));
        assertEquals("", DocumentNumbers.visibleEnd("1234"));
        assertEquals("", DocumentNumbers.mask(""));
        assertEquals("", DocumentNumbers.mask(null));
    }
}
