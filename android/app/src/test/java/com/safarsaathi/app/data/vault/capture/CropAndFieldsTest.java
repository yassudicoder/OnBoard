package com.safarsaathi.app.data.vault.capture;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.safarsaathi.app.data.vault.DocType;

import org.junit.Test;

import java.time.LocalDate;

/** Unit tests for the manual crop's arithmetic ({@link CropMath}) and which fields each document has ({@link DraftFields}). */
public class CropAndFieldsTest {

    @Test
    public void startingCorners() {
        assertArrayEquals(new float[] {0, 0, 1000, 0, 1000, 800, 0, 800}, CropMath.fullCorners(1000, 800), 0.01f);
        assertArrayEquals(new float[] {100, 80, 900, 80, 900, 720, 100, 720},
                CropMath.insetCorners(1000, 800, 0.1f), 0.01f);
    }

    @Test
    public void straightenedPage_usesTheLongerOfOppositeEdges() {
        // A page photographed at an angle: the top edge looks shorter than the bottom one.
        float[] trapezoid = {200, 100, 800, 100, 1000, 900, 0, 900};
        int[] size = CropMath.outputSize(trapezoid, 5000);
        assertEquals(1000, size[0]); // bottom edge
        assertEquals(825, size[1]); // the slanted sides, about 825 px
    }

    @Test
    public void straightenedPage_isScaledDownToTheLimit_keepingItsShape() {
        int[] size = CropMath.outputSize(CropMath.fullCorners(4000, 3000), 2400);
        assertEquals(2400, size[0]);
        assertEquals(1800, size[1]);
    }

    @Test
    public void draggedCornersStayInsideThePhoto() {
        assertEquals(0f, CropMath.clamp(-20f, 0, 1000), 0.001f);
        assertEquals(1000f, CropMath.clamp(1200f, 0, 1000), 0.001f);
        assertEquals(500f, CropMath.clamp(500f, 0, 1000), 0.001f);
    }

    @Test
    public void eachKindOfDocumentShowsOnlyItsOwnFields() {
        assertTrue(DraftFields.fieldsFor(DocType.PASSPORT).contains(DraftFields.Field.EXPIRY_DATE));
        assertFalse(DraftFields.fieldsFor(DocType.PASSPORT).contains(DraftFields.Field.FLIGHT));
        assertFalse(DraftFields.fieldsFor(DocType.HOTEL).contains(DraftFields.Field.NUMBER));
        assertTrue(DraftFields.fieldsFor(DocType.E_VISA).contains(DraftFields.Field.COVERS_COUNTRIES));
        assertEquals(DraftFields.Field.values().length, DraftFields.fieldsFor(DocType.OTHER).size());
    }

    @Test
    public void changingTheKind_clearsFieldsItDoesNotHave() {
        DocumentDraft draft = new DocumentDraft();
        draft.type = DocType.BOARDING_PASS;
        draft.number = "S0000001"; // left over from choosing "Passport" first
        draft.expiryDate = LocalDate.of(2029, 3, 14);
        draft.validFrom = LocalDate.of(2026, 11, 11);
        draft.flightNo = "ZZ 101";

        DraftFields.keepOnlyFieldsOf(draft);

        assertNull(draft.number);
        assertNull(draft.expiryDate);
        assertEquals("ZZ 101", draft.flightNo);
        assertEquals("A boarding pass is for one day", LocalDate.of(2026, 11, 11), draft.validTo);
    }
}
