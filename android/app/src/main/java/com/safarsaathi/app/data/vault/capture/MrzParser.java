package com.safarsaathi.app.data.vault.capture;

import androidx.annotation.Nullable;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Reads the machine-readable zone (MRZ): the 2 or 3 lines of capital letters, digits and "&lt;" at the bottom of a
 * passport's photo page, an ID card or a visa. Layouts and check digits follow ICAO Doc 9303 (Parts 4 to 7).
 * Plain Java, so every rule is unit-tested on the laptop (MrzParserTest).
 *
 * <p>The text comes from the phone's text recognition, which makes typical mistakes: spaces inside the lines, "0"
 * for "O", "5" for "S", lower-case letters, "«" for "&lt;". {@link #find} cleans these up where the layout says what a
 * position must hold (a date position can only be digits, a country code only letters). Anything it cannot be sure
 * of shows up as a failed check digit, and the confirm screen asks the traveller to compare with the document.
 *
 * <p>TD3 (passport), 2 lines of 44:
 * <pre>
 *   P&lt;UTOERIKSSON&lt;&lt;ANNA&lt;MARIA&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;
 *   L898902C36UTO7408122F1204159ZE184226B&lt;&lt;&lt;&lt;&lt;10
 *   0-8 number, 9 check | 10-12 nationality | 13-18 birth date YYMMDD, 19 check | 20 sex |
 *   21-26 expiry, 27 check | 28-41 personal number, 42 check | 43 check over number + dates + personal number
 * </pre>
 * TD2 (2 lines of 36) and the visas MRV-A (2 x 44) and MRV-B (2 x 36) use the same second-line positions with a
 * shorter optional part; visas have no overall check digit. TD1 (ID card, 3 lines of 30) puts the number on line 1,
 * the dates on line 2 and the name on line 3.
 */
public final class MrzParser {

    private MrzParser() {
    }

    /**
     * Looks for an MRZ anywhere in recognised text (one text line per line) and reads it.
     *
     * @param today used to decide the century of 2-digit years
     * @return the best reading found, or null when the text holds no MRZ
     */
    @Nullable
    public static MrzResult find(String ocrText, LocalDate today) {
        // Candidate lines: long ones, or shorter ones with a filler (a name line often loses its trailing fillers).
        List<String> candidates = new ArrayList<>();
        for (String line : ocrText.split("\\r?\\n")) {
            String cleaned = clean(line);
            if (cleaned.length() >= 28 || (cleaned.length() >= 10 && cleaned.indexOf('<') >= 0)) {
                candidates.add(cleaned);
            }
        }
        MrzResult best = null;
        for (int i = 0; i < candidates.size(); i++) {
            for (int size = 2; size <= 3 && i + size <= candidates.size(); size++) {
                MrzResult result = parse(candidates.subList(i, i + size), today);
                if (result != null && (best == null || score(result) > score(best))) {
                    best = result;
                }
            }
        }
        return best;
    }

    /**
     * Reads 2 or 3 lines that should form one MRZ.
     *
     * @return null when they do not look like any ICAO layout
     */
    @Nullable
    public static MrzResult parse(List<String> rawLines, LocalDate today) {
        List<String> lines = new ArrayList<>();
        for (String raw : rawLines) {
            lines.add(clean(raw));
        }
        // The layout is decided by the data line (fixed length, ends with check digits). Lines that may end in
        // fillers (the name line, TD1's first line) may come out shorter; fit() adds the fillers back.
        if (lines.size() == 3 && near(lines.get(1), 30) && between(lines.get(0), 15, 32)
                && between(lines.get(2), 5, 32)) {
            return td1(fit(lines.get(0), 30), fit(lines.get(1), 30), fit(lines.get(2), 30), today);
        }
        if (lines.size() == 2 && lines.get(0).indexOf('<') >= 0) {
            if (near(lines.get(1), 44) && between(lines.get(0), 10, 46)) {
                String first = fit(lines.get(0), 44);
                return twoLines(first, fit(lines.get(1), 44), first.startsWith("V") ? MrzResult.Format.MRV_A
                        : MrzResult.Format.TD3, today);
            }
            if (near(lines.get(1), 36) && between(lines.get(0), 10, 38)) {
                String first = fit(lines.get(0), 36);
                return twoLines(first, fit(lines.get(1), 36), first.startsWith("V") ? MrzResult.Format.MRV_B
                        : MrzResult.Format.TD2, today);
            }
        }
        return null;
    }

    /** TD3, TD2, MRV-A and MRV-B: name on line 1, everything else on line 2 at the same positions. */
    @Nullable
    private static MrzResult twoLines(String line1, String line2, MrzResult.Format format, LocalDate today) {
        String code = documentCode(line1);
        if (code == null) {
            return null;
        }
        // Positions that can only hold digits or only letters are corrected before anything is checked.
        char[] fixed = line2.toCharArray();
        digits(fixed, 9, 10);
        letters(fixed, 10, 13);
        digits(fixed, 13, 20);
        digits(fixed, 21, 28);
        boolean hasComposite = format == MrzResult.Format.TD3 || format == MrzResult.Format.TD2;
        if (hasComposite) {
            digits(fixed, line2.length() - 1, line2.length());
        }
        if (format == MrzResult.Format.TD3) {
            digits(fixed, 42, 43);
        }
        String l2 = new String(fixed);

        int optionalEnd = format == MrzResult.Format.TD3 ? 42 : hasComposite ? l2.length() - 1 : l2.length();
        String optional = l2.substring(28, optionalEnd);
        boolean optionalOk = format != MrzResult.Format.TD3 || MrzCheckDigit.matches(optional, l2.charAt(42));
        boolean compositeOk = !hasComposite || MrzCheckDigit.matches(
                l2.substring(0, 10) + l2.substring(13, 20) + l2.substring(21, l2.length() - 1),
                l2.charAt(l2.length() - 1));

        String[] name = name(lettersOnly(line1.substring(5)));
        return new MrzResult(format, code, country(line1.substring(2, 5)), name[0], name[1],
                withoutFillers(l2.substring(0, 9)), country(l2.substring(10, 13)),
                date(l2.substring(13, 19), true, today), date(l2.substring(21, 27), false, today),
                sex(l2.charAt(20)), withoutFillers(optional),
                MrzCheckDigit.matches(l2.substring(0, 9), l2.charAt(9)),
                MrzCheckDigit.matches(l2.substring(13, 19), l2.charAt(19)),
                MrzCheckDigit.matches(l2.substring(21, 27), l2.charAt(27)),
                compositeOk, optionalOk, l2.charAt(9), Arrays.asList(line1, l2));
    }

    /** TD1 (ID cards): number on line 1, dates and nationality on line 2, name on line 3. */
    @Nullable
    private static MrzResult td1(String line1, String line2, String line3, LocalDate today) {
        String code = documentCode(line1);
        if (code == null) {
            return null;
        }
        char[] first = line1.toCharArray();
        letters(first, 2, 5);
        String l1 = new String(first);
        char[] second = line2.toCharArray();
        digits(second, 0, 7);
        digits(second, 8, 15);
        letters(second, 15, 18);
        digits(second, 29, 30);
        String l2 = new String(second);

        // A number longer than 9 characters: the rest continues in the optional field, ending with its check digit
        // (ICAO 9303 Part 5), and position 14 holds a filler.
        String number = l1.substring(5, 14);
        char numberCheck = l1.charAt(14);
        String optional1 = l1.substring(15, 30);
        if (numberCheck == '<' && optional1.charAt(0) != '<') {
            int end = optional1.indexOf('<');
            String rest = end < 0 ? optional1 : optional1.substring(0, end);
            number = number + rest.substring(0, rest.length() - 1);
            numberCheck = rest.charAt(rest.length() - 1);
            optional1 = end < 0 ? "" : optional1.substring(end);
        }
        boolean compositeOk = MrzCheckDigit.matches(
                l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + l2.substring(18, 29), l2.charAt(29));

        String[] name = name(lettersOnly(line3));
        String optional = withoutFillers(optional1 + "<" + l2.substring(18, 29));
        return new MrzResult(MrzResult.Format.TD1, code, country(l1.substring(2, 5)), name[0], name[1],
                withoutFillers(number), country(l2.substring(15, 18)),
                date(l2.substring(0, 6), true, today), date(l2.substring(8, 14), false, today),
                sex(l2.charAt(7)), optional,
                MrzCheckDigit.matches(number, numberCheck),
                MrzCheckDigit.matches(l2.substring(0, 6), l2.charAt(6)),
                MrzCheckDigit.matches(l2.substring(8, 14), l2.charAt(14)),
                compositeOk, true, number.length() == 9 ? numberCheck : '<', Arrays.asList(l1, l2, line3));
    }

    // ---------------------------------------------------------------------------------------------
    // Cleaning up recognised text
    // ---------------------------------------------------------------------------------------------

    /** Capitals, no spaces, "«" and similar marks become the filler "&lt;", anything else unusable is dropped. */
    static String clean(String line) {
        StringBuilder out = new StringBuilder();
        for (char c : line.toUpperCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '<') {
                out.append(c);
            } else if (c == '«' || c == '‹' || c == '＜' || c == '〈') {
                out.append('<');
            }
            // spaces and other stray marks are dropped
        }
        return out.toString();
    }

    /** Exactly {@code length} characters: missing trailing fillers are added back, extra characters cut off. */
    private static String fit(String line, int length) {
        if (line.length() >= length) {
            return line.substring(0, length);
        }
        StringBuilder padded = new StringBuilder(line);
        while (padded.length() < length) {
            padded.append('<');
        }
        return padded.toString();
    }

    /** Within 2 characters of the layout's length (text recognition sometimes adds or drops one). */
    private static boolean near(String line, int length) {
        return Math.abs(line.length() - length) <= 2;
    }

    private static boolean between(String line, int min, int max) {
        return line.length() >= min && line.length() <= max;
    }

    /** In positions that must be digits, letters that look like digits are read as those digits. */
    private static void digits(char[] line, int from, int to) {
        for (int i = from; i < to && i < line.length; i++) {
            switch (line[i]) {
                case 'O': case 'Q': case 'D': line[i] = '0'; break;
                case 'I': case 'L': line[i] = '1'; break;
                case 'Z': line[i] = '2'; break;
                case 'S': line[i] = '5'; break;
                case 'G': line[i] = '6'; break;
                case 'B': line[i] = '8'; break;
                default: break;
            }
        }
    }

    /** In positions that must be letters, digits that look like letters are read as those letters. */
    private static void letters(char[] line, int from, int to) {
        for (int i = from; i < to && i < line.length; i++) {
            line[i] = letterFor(line[i]);
        }
    }

    private static String lettersOnly(String text) {
        char[] chars = text.toCharArray();
        letters(chars, 0, chars.length);
        return new String(chars);
    }

    private static char letterFor(char c) {
        switch (c) {
            case '0': return 'O';
            case '1': return 'I';
            case '2': return 'Z';
            case '5': return 'S';
            case '6': return 'G';
            case '8': return 'B';
            default: return c;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------------------------------------

    /** "P", "PT", "V", "ID", ...: the first character must be a known document letter. */
    @Nullable
    private static String documentCode(String line1) {
        char kind = letterFor(line1.charAt(0));
        if ("PVIAC".indexOf(kind) < 0) {
            return null;
        }
        char second = line1.charAt(1);
        return second == '<' ? String.valueOf(kind) : String.valueOf(kind) + letterFor(second);
    }

    /** "IND" stays "IND"; Germany's "D&lt;&lt;" becomes "D". */
    private static String country(String code) {
        return lettersOnly(code).replace("<", "");
    }

    /**
     * "ERIKSSON&lt;&lt;ANNA&lt;MARIA&lt;&lt;&lt;..." becomes {"ERIKSSON", "ANNA MARIA"}. The surname comes first,
     * separated from the given names by "&lt;&lt;"; single "&lt;" are spaces inside a name.
     */
    private static String[] name(String field) {
        String trimmed = field.replaceAll("<+$", "");
        int split = trimmed.indexOf("<<");
        String surname = split < 0 ? trimmed : trimmed.substring(0, split);
        String given = split < 0 ? "" : trimmed.substring(split + 2);
        return new String[] {blankToNull(surname.replace('<', ' ').trim()),
                blankToNull(given.replace('<', ' ').replaceAll(" +", " ").trim())};
    }

    /**
     * YYMMDD to a date. Birth dates: the latest year that is not in the future (so "90" is 1990 and "05" is 2005).
     * Expiry dates: 20YY, unless that is more than 50 years away (then 19YY, a long-expired document).
     * Null when a part is unknown ("&lt;&lt;") or the date does not exist.
     */
    @Nullable
    static LocalDate date(String yymmdd, boolean birth, LocalDate today) {
        if (!yymmdd.matches("[0-9]{6}")) {
            return null;
        }
        int yy = Integer.parseInt(yymmdd.substring(0, 2));
        int month = Integer.parseInt(yymmdd.substring(2, 4));
        int day = Integer.parseInt(yymmdd.substring(4, 6));
        int year = 2000 + yy;
        if (birth ? year > today.getYear() : year > today.getYear() + 50) {
            year -= 100;
        }
        try {
            LocalDate date = LocalDate.of(year, month, day);
            if (birth && date.isAfter(today)) {
                date = date.minusYears(100);
            }
            return date;
        } catch (DateTimeException e) {
            return null;
        }
    }

    @Nullable
    private static String sex(char c) {
        return c == 'M' || c == 'F' || c == 'X' ? String.valueOf(c) : null;
    }

    @Nullable
    private static String withoutFillers(String field) {
        return blankToNull(field.replace('<', ' ').trim().replaceAll(" +", " "));
    }

    @Nullable
    private static String blankToNull(String text) {
        return text.isEmpty() ? null : text;
    }

    /** Prefers readings that pass more check digits (used when the text holds several candidate lines). */
    private static int score(MrzResult result) {
        int score = 0;
        score += result.numberCheckOk ? 2 : 0;
        score += result.birthDateCheckOk ? 1 : 0;
        score += result.expiryCheckOk ? 1 : 0;
        score += result.compositeCheckOk ? 2 : 0;
        score += result.optionalCheckOk ? 1 : 0;
        return score;
    }
}
