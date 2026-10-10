package com.safarsaathi.app.data.vault.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for the MRZ reader ({@link MrzParser}). The golden examples are the specimen lines printed in ICAO
 * Doc 9303 (the made-up "Anna Maria Eriksson" of the made-up state "Utopia", code UTO), whose check digits are all
 * correct, plus the same lines with deliberate mistakes.
 */
public class MrzParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    private static final String TD3_1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
    private static final String TD3_2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

    @Test
    public void checkDigit_icaoExamples() {
        assertEquals(6, MrzCheckDigit.of("L898902C3"));
        assertEquals(2, MrzCheckDigit.of("740812"));
        assertEquals(9, MrzCheckDigit.of("120415"));
        assertTrue(MrzCheckDigit.matches("<<<<<<<<<<<<<<", '<'));
        assertTrue(MrzCheckDigit.matches("<<<<<<<<<<<<<<", '0'));
        assertFalse(MrzCheckDigit.matches("L898902C3", '5'));
    }

    @Test
    public void td3Passport_allFieldsAndChecks() {
        MrzResult mrz = MrzParser.parse(Arrays.asList(TD3_1, TD3_2), TODAY);

        assertEquals(MrzResult.Format.TD3, mrz.format);
        assertTrue(mrz.isPassport());
        assertEquals("P", mrz.documentCode);
        assertEquals("UTO", mrz.issuingState);
        assertEquals("ERIKSSON", mrz.surname);
        assertEquals("ANNA MARIA", mrz.givenNames);
        assertEquals("ANNA MARIA ERIKSSON", mrz.fullName());
        assertEquals("L898902C3", mrz.documentNumber);
        assertEquals("UTO", mrz.nationality);
        assertEquals(LocalDate.of(1974, 8, 12), mrz.birthDate);
        assertEquals("F", mrz.sex);
        assertEquals(LocalDate.of(2012, 4, 15), mrz.expiryDate);
        assertEquals("ZE184226B", mrz.optionalData);
        assertTrue(mrz.allChecksOk());
    }

    @Test
    public void td3_aWrongCharacterInTheNumber_failsItsCheckDigit_andTheOverallOne() {
        MrzResult mrz = MrzParser.parse(Arrays.asList(TD3_1, "L898902C46UTO7408122F1204159ZE184226B<<<<<10"),
                TODAY);

        assertFalse(mrz.numberCheckOk);
        assertFalse(mrz.compositeCheckOk);
        assertTrue(mrz.birthDateCheckOk);
        assertTrue(mrz.expiryCheckOk);
        assertFalse(mrz.allChecksOk());
    }

    @Test
    public void td3_wrongDateCheckDigits_areReportedPerField() {
        MrzResult mrz = MrzParser.parse(Arrays.asList(TD3_1, "L898902C36UTO7408123F1204158ZE184226B<<<<<10"),
                TODAY);

        assertTrue(mrz.numberCheckOk);
        assertFalse(mrz.birthDateCheckOk);
        assertFalse(mrz.expiryCheckOk);
    }

    @Test
    public void find_cleansUpTypicalTextRecognitionMistakes() {
        // Spaces, lower case, "«" for "<", the letter O inside a date, and trailing fillers dropped.
        String recognised = "REPUBLIC OF UTOPIA\nPassport No. L898902C3\n"
                + "p<utoeriksson«anna<maria\n"
                + "L898902C36UTO74O8122F1204159ZE184226B<<<<<10\n";
        // "«" stands for one "<" here, so the surname/given-name separator "<<" is not there: given names are lost,
        // which the confirm screen shows to the traveller. The numbers still pass every check.
        MrzResult mrz = MrzParser.find(recognised, TODAY);

        assertEquals("L898902C3", mrz.documentNumber);
        assertEquals(LocalDate.of(1974, 8, 12), mrz.birthDate);
        assertTrue(mrz.allChecksOk());

        MrzResult spaced = MrzParser.find("P<UTO ERIKSSON<<ANNA<MARIA <<<<<<<<<<<<<<<<<<<\n"
                + "L898902C3 6 UTO 740812 2 F 120415 9 ZE184226B<<<<< 1 0", TODAY);
        assertEquals("ERIKSSON", spaced.surname);
        assertEquals("ANNA MARIA", spaced.givenNames);
        assertTrue(spaced.allChecksOk());
    }

    @Test
    public void find_returnsNullWhenThereIsNoMrz() {
        assertNull(MrzParser.find("BOARDING PASS\nGATE 32\nSEAT 14A", TODAY));
        assertNull(MrzParser.find("", TODAY));
        // Long lines that do not start like a document code are not an MRZ.
        assertNull(MrzParser.find("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n"
                + "1234567890123456789012345678901234567890ABCD", TODAY));
    }

    @Test
    public void td1IdCard() {
        List<String> lines = Arrays.asList("I<UTOD231458907<<<<<<<<<<<<<<<", "7408122F1204159UTO<<<<<<<<<<<6",
                "ERIKSSON<<ANNA<MARIA<<<<<<<<<<");
        MrzResult mrz = MrzParser.parse(lines, TODAY);

        assertEquals(MrzResult.Format.TD1, mrz.format);
        assertEquals("I", mrz.documentCode);
        assertEquals("D23145890", mrz.documentNumber);
        assertEquals("ERIKSSON", mrz.surname);
        assertEquals(LocalDate.of(2012, 4, 15), mrz.expiryDate);
        assertTrue(mrz.allChecksOk());
    }

    @Test
    public void td2() {
        MrzResult mrz = MrzParser.parse(Arrays.asList("I<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<",
                "D231458907UTO7408122F1204159<<<<<<<6"), TODAY);

        assertEquals(MrzResult.Format.TD2, mrz.format);
        assertEquals("D23145890", mrz.documentNumber);
        assertTrue(mrz.allChecksOk());
    }

    @Test
    public void visas_mrvA_andMrvB() {
        MrzResult a = MrzParser.parse(Arrays.asList("V<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L8988901C4XXX4009078F96121096ZE184226B<<<<<<"), TODAY);
        assertEquals(MrzResult.Format.MRV_A, a.format);
        assertTrue(a.isVisa());
        assertEquals("L8988901C", a.documentNumber);
        assertEquals("XXX", a.nationality);
        assertEquals(LocalDate.of(1940, 9, 7), a.birthDate);
        assertEquals(LocalDate.of(1996, 12, 10), a.expiryDate); // long expired: 19YY
        assertTrue(a.allChecksOk()); // visas have no overall check digit

        MrzResult b = MrzParser.parse(Arrays.asList("V<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<",
                "L8988901C4XXX4009078F9612109<<<<<<<<"), TODAY);
        assertEquals(MrzResult.Format.MRV_B, b.format);
        assertTrue(b.allChecksOk());
    }

    @Test
    public void birthYears_neverInTheFuture() {
        assertEquals(LocalDate.of(2005, 1, 2), MrzParser.date("050102", true, TODAY));
        assertEquals(LocalDate.of(1990, 5, 14), MrzParser.date("900514", true, TODAY));
        // "26" in December 2026 has not happened yet on 11 Oct 2026: a person born then is from 1926.
        assertEquals(LocalDate.of(1926, 12, 1), MrzParser.date("261201", true, TODAY));
        assertEquals(LocalDate.of(2034, 3, 14), MrzParser.date("340314", false, TODAY));
        assertNull(MrzParser.date("741332", true, TODAY)); // no 13th month
        assertNull(MrzParser.date("74<<<<", true, TODAY)); // unknown date
    }

    @Test
    public void writer_makesALineThatReadsBackWithEveryCheckPassing() {
        List<String> lines = MrzWriter.passport("IND", "Singh", "Paramvir", "S0000001", "IND",
                LocalDate.of(1990, 5, 14), 'M', LocalDate.of(2029, 3, 14), "");

        assertEquals(44, lines.get(0).length());
        assertEquals(44, lines.get(1).length());
        assertEquals("P<INDSINGH<<PARAMVIR<<<<<<<<<<<<<<<<<<<<<<<<", lines.get(0));
        assertEquals("S0000001<9IND9005145M2903149<<<<<<<<<<<<<<04", lines.get(1));
        MrzResult mrz = MrzParser.parse(lines, TODAY);
        assertTrue(mrz.allChecksOk());
        assertEquals("PARAMVIR SINGH", mrz.fullName());
    }
}
