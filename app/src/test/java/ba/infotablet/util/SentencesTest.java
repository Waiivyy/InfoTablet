package ba.infotablet.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SentencesTest {

    @Test
    public void ordinalNumbersDoNotEndSentences() {
        List<String> s = Sentences.split("Gol je pao u 69. minuti. Utakmica je završena 14. novembra 2026. godine.");
        assertEquals(2, s.size());
        assertEquals("Gol je pao u 69. minuti.", s.get(0));
    }

    @Test
    public void initialsAndLegalFormsDoNotEndSentences() {
        assertEquals(2, Sentences.split("Uhapšen je M. O. iz Laktaša. Istraga traje.").size());
        assertEquals(2, Sentences.split("Firma d.o.o. Sarajevo je pobijedila na tenderu. Žalbe nije bilo.").size());
        assertEquals(2, Sentences.split("Problem su prijavili U.S. Bank i drugi. Kvar je otklonjen.").size());
    }

    @Test
    public void quotesAndQuestionMarks() {
        List<String> s = Sentences.split("Rekao je: \"Sve je spremno.\" Zatim je otišao. Šta dalje?");
        assertEquals(3, s.size());
    }

    @Test
    public void firstStopsAtSentenceAndCharacterLimits() {
        String t = "Prva rečenica je ovdje. Druga rečenica je ovdje. Treća rečenica je ovdje. Četvrta se ne vidi.";
        assertEquals("Prva rečenica je ovdje. Druga rečenica je ovdje. Treća rečenica je ovdje.", Sentences.first(t, 3, 350));
        assertEquals("Prva rečenica je ovdje.", Sentences.first(t, 3, 30));
    }
}
