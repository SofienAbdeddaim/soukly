package com.soukly.listing.referential;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads the referential tables, filled by the Flyway migrations only. */
@Repository
class ReferentialRepository {

    private final JdbcClient jdbc;

    ReferentialRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Active families with their active subcategories, both in display order. */
    List<CategoryFamily> activeCategories() {
        Map<String, Labels> families = new LinkedHashMap<>();
        Map<String, List<Subcategory>> subcategories = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT f.code, f.label_fr, f.label_ar, f.label_en
                FROM categories f
                WHERE f.parent_code IS NULL AND f.active
                ORDER BY f.sort_order, f.code""")
                .query(rs -> {
                    families.put(rs.getString("code"), labels(rs));
                    subcategories.put(rs.getString("code"), new ArrayList<>());
                });
        jdbc.sql("""
                SELECT s.code, s.parent_code, s.label_fr, s.label_ar, s.label_en
                FROM categories s
                WHERE s.parent_code IS NOT NULL AND s.active
                ORDER BY s.sort_order, s.code""")
                .query(rs -> {
                    List<Subcategory> children = subcategories.get(rs.getString("parent_code"));
                    // null when the family is inactive: its subcategories are hidden too
                    if (children != null) {
                        children.add(new Subcategory(rs.getString("code"), rs.getString("parent_code"), labels(rs)));
                    }
                });
        return families.entrySet().stream()
                .map(family -> new CategoryFamily(family.getKey(), family.getValue(),
                        List.copyOf(subcategories.get(family.getKey()))))
                .toList();
    }

    /** Attributes of every category that has some, in display order, keyed by category code. */
    Map<String, List<Attribute>> attributesByCategory() {
        Map<String, List<EnumValue>> values = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT attribute_code, code, label_fr, label_ar, label_en
                FROM attribute_enum_values
                ORDER BY attribute_code, sort_order, code""")
                .query(rs -> {
                    values.computeIfAbsent(rs.getString("attribute_code"), code -> new ArrayList<>())
                            .add(new EnumValue(rs.getString("code"), labels(rs)));
                });
        Map<String, List<Attribute>> attributes = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT ca.category_code, d.code, d.type, COALESCE(ca.required, d.required) AS required,
                       d.min_value, d.max_value, d.max_value_current_year_offset, d.min_length, d.max_length,
                       d.filterable, d.label_fr, d.label_ar, d.label_en
                FROM category_attributes ca
                JOIN attribute_definitions d ON d.code = ca.attribute_code
                ORDER BY ca.category_code, ca.sort_order, d.sort_order, d.code""")
                .query(rs -> {
                    String code = rs.getString("code");
                    attributes.computeIfAbsent(rs.getString("category_code"), category -> new ArrayList<>())
                            .add(new Attribute(code, AttributeType.valueOf(rs.getString("type")),
                                    rs.getBoolean("required"), integer(rs, "min_value"), integer(rs, "max_value"),
                                    integer(rs, "max_value_current_year_offset"), integer(rs, "min_length"),
                                    integer(rs, "max_length"), rs.getBoolean("filterable"), labels(rs),
                                    List.copyOf(values.getOrDefault(code, List.of()))));
                });
        attributes.replaceAll((category, list) -> List.copyOf(list));
        return attributes;
    }

    List<Governorate> governorates() {
        return jdbc.sql("""
                SELECT code, label_fr, label_ar, label_en
                FROM governorates
                ORDER BY sort_order, code""")
                .query((rs, row) -> new Governorate(rs.getString("code"), labels(rs)))
                .list();
    }

    /** Every delegation, in no particular order. */
    List<Delegation> delegations() {
        return jdbc.sql("""
                SELECT code, governorate_code, label_fr, label_ar, label_en
                FROM delegations""")
                .query((rs, row) -> new Delegation(rs.getString("code"), rs.getString("governorate_code"),
                        labels(rs)))
                .list();
    }

    private static Labels labels(ResultSet rs) throws SQLException {
        return new Labels(rs.getString("label_fr"), rs.getString("label_ar"), rs.getString("label_en"));
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
