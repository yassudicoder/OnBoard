package com.safarsaathi.app.data.ask;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class LanguagePickerTest {

    private final LanguagePicker picker = new LanguagePicker(Lexicon.get());

    private static LanguageHint hint(String tag, float confidence) {
        Map<String, Float> map = new HashMap<>();
        map.put(tag, confidence);
        return LanguageHint.of(map);
    }

    @Test
    public void markers_pickTheLanguage() {
        assertEquals(AskLanguage.HI, picker.pick("मेरा गेट कहाँ है?", null, LanguageHint.NONE));
        assertEquals(AskLanguage.MR, picker.pick("माझी बॅग कुठे आहे?", null, LanguageHint.NONE));
        assertEquals(AskLanguage.HINGLISH, picker.pick("Mera gate kahan hai?", null, LanguageHint.NONE));
        assertEquals(AskLanguage.EN, picker.pick("Where is my gate?", null, LanguageHint.NONE));
    }

    @Test
    public void marathiInEnglishLetters_isAnsweredInMarathi() {
        assertEquals(AskLanguage.MR, picker.pick("Majhi bag kuthe aahe?", null, LanguageHint.NONE));
        assertEquals(AskLanguage.MR, picker.pick("Majhi connection miss honar ka?", null, LanguageHint.NONE));
    }

    @Test
    public void travellersChoice_overridesEverything() {
        assertEquals(AskLanguage.MR, picker.pick("Where is my gate?", AskLanguage.MR, LanguageHint.NONE));
    }

    @Test
    public void devanagariWithoutMarkers_mlKitBreaksTheTie_elseHindi() {
        // "गेट" alone has no Hindi or Marathi marker.
        assertEquals(AskLanguage.MR, picker.pick("गेट?", null, hint("mr", 0.8f)));
        assertEquals(AskLanguage.HI, picker.pick("गेट?", null, hint("mr", 0.3f)));
        assertEquals(AskLanguage.HI, picker.pick("गेट?", null, LanguageHint.NONE));
    }

    @Test
    public void englishLettersWithoutMarkers_confidentHindiLatin_isHinglish() {
        assertEquals(AskLanguage.HINGLISH, picker.pick("gate number?", null, hint("hi-Latn", 0.9f)));
        assertEquals(AskLanguage.EN, picker.pick("gate number?", null, LanguageHint.NONE));
    }

    @Test
    public void moreEnglishThanHindiMarkers_staysEnglish() {
        assertEquals(AskLanguage.EN, picker.pick("Where is my gate yaar?", null, LanguageHint.NONE));
    }

    @Test
    public void undetermined_isNoHint() {
        assertEquals(0f, hint("und", 1f).confidence("und"), 0f);
    }
}
