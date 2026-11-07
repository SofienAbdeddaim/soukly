package com.soukly.social.caption;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

class CaptionBuilderTest {

    private static final URI URL = URI.create("https://soukly.example/fr/listings/8f3c2a10");
    private static final String DESCRIPTION = "Première main, carnet d'entretien complet chez le concessionnaire.";

    private final CaptionBuilder builder = new CaptionBuilder();

    @Test
    void aFrenchTextFollowsTheTemplate() {
        Caption caption = builder.build(car(CaptionLanguage.FR, "Voitures", "Sfax", "Sakiet Ezzit"));

        assertThat(plain(caption.text())).isEqualTo("""
                Peugeot 208 diesel, première main
                32 000 DT · Négociable
                Sfax, Sakiet Ezzit

                Première main, carnet d'entretien complet chez le concessionnaire.

                Voir l'annonce : https://soukly.example/fr/listings/8f3c2a10
                #Soukly #Sfax #Voitures""");
        assertThat(caption.hashtags()).containsExactly("#Soukly", "#Sfax", "#Voitures");
    }

    @Test
    void anArabicTextHasArabicWordsHashtagsAndPrice() {
        Caption caption = builder.build(car(CaptionLanguage.AR, "سيارات", "صفاقس", "ساقية الزيت"));

        assertThat(plain(caption.text())).isEqualTo("""
                Peugeot 208 diesel, première main
                32.000 د.ت. · قابل للتفاوض
                صفاقس، ساقية الزيت

                Première main, carnet d'entretien complet chez le concessionnaire.

                شاهد الإعلان: https://soukly.example/fr/listings/8f3c2a10
                #Soukly #صفاقس #سيارات""");
    }

    @Test
    void anEnglishTextHasEnglishWordsAndPrice() {
        Caption caption = builder.build(car(CaptionLanguage.EN, "Cars", "Sfax", null));

        assertThat(plain(caption.text())).isEqualTo("""
                Peugeot 208 diesel, première main
                TND 32,000 · Negotiable
                Sfax

                Première main, carnet d'entretien complet chez le concessionnaire.

                See the listing: https://soukly.example/fr/listings/8f3c2a10
                #Soukly #Sfax #Cars""");
    }

    @Test
    void pricesUseTheNonBreakingSpacesOfTheFrontEnd() {
        assertThat(CaptionLanguage.FR.price(32_000)).isEqualTo("32 000 DT");
        assertThat(CaptionLanguage.AR.price(32_000)).isEqualTo("32.000 د.ت.");
        assertThat(CaptionLanguage.EN.price(1_250_000)).isEqualTo("TND 1,250,000");
        assertThat(CaptionLanguage.FR.price(180)).isEqualTo("180 DT");
    }

    @Test
    void aPriceThatIsNotNegotiableIsAloneOnItsLine() {
        Caption caption = builder.build(new CaptionInput("Canapé d'angle", null, CaptionInput.FIXED, 1_250L, false,
                "Meubles", "Tunis", null, URL, CaptionLanguage.FR));

        assertThat(plain(caption.text())).startsWith("Canapé d'angle\n1 250 DT\nTunis\n\nVoir l'annonce");
    }

    @Test
    void aListingOnQuoteSaysSoInEachLanguage() {
        assertThat(builder.build(onQuote(CaptionLanguage.FR)).text()).contains("\nSur devis\n");
        assertThat(builder.build(onQuote(CaptionLanguage.AR)).text()).contains("\nحسب الطلب\n");
        assertThat(builder.build(onQuote(CaptionLanguage.EN)).text()).contains("\nOn quote\n");
    }

    @Test
    void aLongDescriptionIsCutOnTheLastWordWithAnEllipsis() {
        String description = "Très bon état. ".repeat(30);

        String text = builder.build(withDescription(description)).text();

        String cut = text.split("\n\n")[1];
        assertThat(CaptionBuilder.length(cut)).isLessThanOrEqualTo(CaptionBuilder.DESCRIPTION_MAX);
        assertThat(cut).endsWith("Très bon état…").doesNotContain("…".repeat(2));
        assertThat(description).startsWith(cut.substring(0, cut.length() - 1));
    }

    @Test
    void aShortDescriptionIsKeptWhole() {
        String description = "x".repeat(CaptionBuilder.DESCRIPTION_MAX);

        assertThat(CaptionBuilder.truncate(description, CaptionBuilder.DESCRIPTION_MAX)).isEqualTo(description);
    }

    @Test
    void aSingleWordLongerThanTheLimitIsCutInside() {
        String cut = CaptionBuilder.truncate("a".repeat(400), 300);

        assertThat(cut).hasSize(300).endsWith("a…");
    }

    @Test
    void theCutCountsCodePointsAndKeepsArabicWordsWhole() {
        String description = "سيارة في حالة ممتازة ".repeat(20);

        String cut = CaptionBuilder.truncate(description, 50);

        assertThat(CaptionBuilder.length(cut)).isLessThanOrEqualTo(50);
        assertThat(cut).endsWith("حالة ممتازة سيارة…").doesNotContain("  ");
    }

    @Test
    void theTextNeverGoesBeyondTheMaximumLength() {
        String title = "T".repeat(1_900);

        Caption caption = builder.build(new CaptionInput(title, "Description ".repeat(40), CaptionInput.FIXED,
                32_000L, true, "Voitures", "Sfax", "Sakiet Ezzit", URL, CaptionLanguage.FR));

        assertThat(CaptionBuilder.length(caption.text())).isLessThanOrEqualTo(CaptionBuilder.MAX_LENGTH);
    }

    @Test
    void theLongestRealListingFitsWithItsWholeTemplate() {
        // Title 80, description cut at 300: far below the limit
        Caption caption = builder.build(new CaptionInput("T".repeat(80), "mot ".repeat(1_000), CaptionInput.FIXED,
                100_000_000L, true, "Pièces et accessoires", "Sidi Bouzid", "Sidi Bouzid Ouest", URL,
                CaptionLanguage.FR));

        assertThat(CaptionBuilder.length(caption.text())).isLessThan(600);
        assertThat(caption.text()).contains("…\n\nVoir l'annonce");
    }

    @Test
    void hashtagsHaveNeitherSpacesNorPunctuation() {
        assertThat(CaptionBuilder.hashtag("Ben Arous")).isEqualTo("#BenArous");
        assertThat(CaptionBuilder.hashtag("Motos et scooters")).isEqualTo("#MotosEtScooters");
        assertThat(CaptionBuilder.hashtag("Offres d'emploi")).isEqualTo("#OffresDEmploi");
        assertThat(CaptionBuilder.hashtag("TV, son et vidéo")).isEqualTo("#TVSonEtVidéo");
        assertThat(CaptionBuilder.hashtag("Médenine")).isEqualTo("#Médenine");
        assertThat(CaptionBuilder.hashtag("دراجات نارية وسكوترات")).isEqualTo("#دراجات_نارية_وسكوترات");
        assertThat(CaptionBuilder.hashtag("أراضٍ")).isEqualTo("#أراضٍ");
        assertThat(CaptionBuilder.hashtag(" - ")).isNull();
    }

    @Test
    void theSameLabelTwiceGivesOneHashtag() {
        Caption caption = builder.build(new CaptionInput("Studio meublé", null, null, null, false, "Tunis", "Tunis",
                null, URL, CaptionLanguage.FR));

        assertThat(caption.hashtags()).containsExactly("#Soukly", "#Tunis");
    }

    @Test
    void emptyFieldsOfADraftLeaveTheirLineOut() {
        Caption caption = builder.build(new CaptionInput(null, " ", null, null, false, null, null, null, URL,
                CaptionLanguage.FR));

        assertThat(caption.text()).isEqualTo("Voir l'annonce : " + URL + "\n#Soukly");
    }

    @Test
    void phoneNumbersAndEmailsTypedByTheSellerAreRemoved() {
        Caption caption = builder.build(new CaptionInput("Golf 7 à vendre, appelez le 20 123 456",
                "Contact : vendeur.sfax@example.tn ou +216 98.12.34.56, ou 0021671123456. Prix 25 000 000 DT, "
                        + "année 2019, 62 000 km.",
                CaptionInput.FIXED, 25_000_000L, false, "Voitures", "Sfax", null, URL, CaptionLanguage.FR));

        assertThat(caption.text())
                .doesNotContain("20 123 456", "vendeur.sfax", "98.12.34.56", "71123456")
                .contains("appelez le [ ]".replace("[ ]", PersonalData.MASK))
                .contains("25 000 000 DT", "2019", "62 000 km");
    }

    @Test
    void anInstagramCaptionHasTheShortAddressInsteadOfTheLink() {
        CaptionInput input = new CaptionInput("Peugeot 208 diesel, première main", DESCRIPTION, CaptionInput.FIXED,
                32_000L, true, "Voitures", "Sfax", "Sakiet Ezzit",
                URI.create("https://exemple.test/fr/listings/8f3c2a10"), CaptionLanguage.FR);

        Caption caption = builder.buildForInstagram(input, "8F3C2K9A");

        assertThat(plain(caption.text())).isEqualTo("""
                Peugeot 208 diesel, première main
                32 000 DT · Négociable
                Sfax, Sakiet Ezzit

                Première main, carnet d'entretien complet chez le concessionnaire.

                Annonce complète sur exemple.test/r/8F3C2K9A
                #Soukly #Sfax #Voitures""");
        assertThat(caption.text()).doesNotContain("https://").doesNotContain("http://");
    }

    @Test
    void theInstagramAddressIsInTheLanguageOfTheText() {
        assertThat(builder.buildForInstagram(car(CaptionLanguage.AR, "سيارات", "صفاقس", null), "8F3C2K9A").text())
                .contains("\nالإعلان كاملًا على soukly.example/r/8F3C2K9A\n#Soukly #صفاقس #سيارات");
        assertThat(builder.buildForInstagram(car(CaptionLanguage.EN, "Cars", "Sfax", null), "8F3C2K9A").text())
                .contains("\nFull listing on soukly.example/r/8F3C2K9A\n#Soukly #Sfax #Cars");
    }

    @Test
    void theHostKeepsItsPortAndAListingWithoutReferenceGetsTheHostAlone() {
        CaptionInput local = new CaptionInput("Studio", null, null, null, false, null, null, null,
                URI.create("http://localhost:4200/fr/listings/8f3c2a10"), CaptionLanguage.FR);

        assertThat(builder.buildForInstagram(local, "8F3C2K9A").text())
                .contains("Annonce complète sur localhost:4200/r/8F3C2K9A");
        assertThat(builder.buildForInstagram(car(CaptionLanguage.FR, "Voitures", "Sfax", null), null).text())
                .contains("\nAnnonce complète sur soukly.example\n#Soukly");
    }

    @Test
    void anInstagramCaptionNeverCarriesALinkNorPersonalData() {
        CaptionInput input = new CaptionInput("Golf 7 https://example.org/golf", "Photos : https://example.org/a "
                + "et HTTP://example.org/b, appelez le 20 123 456 ou seller@example.tn", CaptionInput.FIXED, 25_000L,
                false, "Voitures", "Sfax", null, URL, CaptionLanguage.FR);

        String text = builder.buildForInstagram(input, "8F3C2K9A").text();

        assertThat(text).doesNotContainIgnoringCase("http://").doesNotContainIgnoringCase("https://")
                .doesNotContain("20 123 456").doesNotContain("seller@example.tn").contains("example.org/golf");
    }

    @Test
    void anInstagramCaptionHasAtMostThirtyHashtags() {
        String typed = java.util.stream.IntStream.rangeClosed(1, 40).mapToObj(i -> "#tag" + i)
                .collect(java.util.stream.Collectors.joining(" "));
        CaptionInput input = new CaptionInput("Vélo #vtt", typed, CaptionInput.FIXED, 300L, false, "Vélos", "Sfax",
                null, URL, CaptionLanguage.FR);

        String text = builder.buildForInstagram(input, "8F3C2K9A").text();

        assertThat(java.util.regex.Pattern.compile("#(?=[\\p{L}\\p{N}_])").matcher(text).results().count())
                .isEqualTo(CaptionBuilder.INSTAGRAM_MAX_HASHTAGS);
        assertThat(text).contains("#vtt", "#tag26 tag27", "#Soukly #Sfax #Vélos");
        assertThat(CaptionBuilder.length(text)).isLessThanOrEqualTo(2_200);
    }

    @Test
    void theFacebookTextKeepsItsLink() {
        assertThat(builder.build(car(CaptionLanguage.FR, "Voitures", "Sfax", null)).text())
                .contains("Voir l'annonce : https://soukly.example/fr/listings/8f3c2a10");
    }

    private static CaptionInput car(CaptionLanguage language, String category, String governorate,
            String delegation) {
        return new CaptionInput("Peugeot 208 diesel, première main", DESCRIPTION, CaptionInput.FIXED, 32_000L, true,
                category, governorate, delegation, URL, language);
    }

    private static CaptionInput onQuote(CaptionLanguage language) {
        return new CaptionInput("Cours de mathématiques", DESCRIPTION, CaptionInput.ON_QUOTE, null, false,
                "Cours et soutien scolaire", "Tunis", null, URL, language);
    }

    private static CaptionInput withDescription(String description) {
        return new CaptionInput("Peugeot 208", description, CaptionInput.FIXED, 32_000L, true, "Voitures", "Sfax",
                null, URL, CaptionLanguage.FR);
    }

    /** The non-breaking spaces of the prices as plain spaces, to read the expected texts. */
    private static String plain(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }
}
