package com.soukly.listing.referential;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import com.soukly.listing.support.IntegrationTest;

/** Checks the data loaded by the migrations, straight in the database (inactive rows included). */
class ReferentialDataTests extends IntegrationTest {

    private static final String COUNTS = """
            SELECT (SELECT count(*) FROM categories WHERE parent_code IS NULL) AS families,
                   (SELECT count(*) FROM categories WHERE parent_code IS NOT NULL) AS subcategories,
                   (SELECT count(*) FROM attribute_definitions) AS attributes,
                   (SELECT count(*) FROM attribute_enum_values) AS enum_values,
                   (SELECT count(*) FROM category_attributes) AS category_attributes,
                   (SELECT count(*) FROM governorates) AS governorates,
                   (SELECT count(*) FROM delegations) AS delegations""";

    @Autowired
    private Flyway flyway;

    @Autowired
    private ReferentialService referentials;

    @Test
    void v1DataIsLoaded() {
        Map<String, Object> counts = jdbc.queryForMap(COUNTS);

        assertThat(counts).containsEntry("families", 10L)
                .containsEntry("subcategories", 44L)
                .containsEntry("attributes", 10L)
                .containsEntry("enum_values", 9L)
                .containsEntry("governorates", 24L);
    }

    @ParameterizedTest
    @ValueSource(strings = { "categories", "attribute_definitions", "attribute_enum_values", "governorates",
            "delegations" })
    void everyElementHasThreeNonEmptyLabels(String table) {
        Integer missing = jdbc.queryForObject("SELECT count(*) FROM " + table
                + " WHERE trim(label_fr) = '' OR trim(label_ar) = '' OR trim(label_en) = ''", Integer.class);

        assertThat(missing).isZero();
    }

    @Test
    void onlyVehiclesAndRealEstateHaveSpecificAttributes() {
        List<String> families = jdbc.queryForList(
                "SELECT DISTINCT split_part(category_code, '.', 1) FROM category_attributes ORDER BY 1", String.class);

        assertThat(families).containsExactly("real-estate", "vehicles");
    }

    @Test
    void migrationsCanBeReplayedWithoutDuplicatesOrChangedCodes() throws Exception {
        Map<String, Object> before = jdbc.queryForMap(COUNTS);
        List<String> codesBefore = jdbc.queryForList("SELECT code FROM categories ORDER BY code", String.class);

        // A restart runs Flyway again: nothing is pending.
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        // Running the data scripts again (ON CONFLICT DO NOTHING) changes nothing either.
        for (String script : List.of("V2__insert_categories.sql", "V3__insert_attributes.sql",
                "V4__insert_governorates.sql")) {
            jdbc.execute(new ClassPathResource("db/migration/" + script).getContentAsString(StandardCharsets.UTF_8));
        }

        assertThat(jdbc.queryForMap(COUNTS)).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT code FROM categories ORDER BY code", String.class))
                .isEqualTo(codesBefore);
    }

    @Test
    void serviceExposesTheReferentialsToTheOtherFeatures() {
        assertThat(referentials.findSubcategory("vehicles.cars")).hasValueSatisfying(
                subcategory -> assertThat(subcategory.familyCode()).isEqualTo("vehicles"));
        assertThat(referentials.findSubcategory("vehicles")).isEmpty();
        assertThat(referentials.findSubcategory("foo.bar")).isEmpty();
        assertThat(referentials.attributesOf("vehicles.cars")).extracting(Attribute::code)
                .containsExactly("brand", "model", "year", "mileage-km", "fuel", "gearbox");
        assertThat(referentials.findGovernorate("sfax")).isPresent();
        assertThat(referentials.findGovernorate("atlantis")).isEmpty();
        assertThat(referentials.findDelegation("sfax.unknown")).isEmpty();
    }
}
