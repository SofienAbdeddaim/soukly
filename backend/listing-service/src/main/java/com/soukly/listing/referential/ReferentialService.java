package com.soukly.listing.referential;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Read-only referentials of Soukly: categories, specific attributes, governorates and delegations.
 *
 * <p>The data only changes through Flyway migrations in V1, so it is read once, on first use, and kept in memory
 * for the life of the service. Besides the public API, ANN-01 uses this bean to check the category, the attributes
 * and the place of a listing.
 */
@Service
public class ReferentialService {

    private final ReferentialRepository repository;
    private volatile Snapshot snapshot;

    ReferentialService(ReferentialRepository repository) {
        this.repository = repository;
    }

    /** Active families with their active subcategories, in display order. */
    public List<CategoryFamily> categories() {
        return snapshot().families();
    }

    /** The active subcategory with this code; empty for an unknown or inactive code, or a family code. */
    public Optional<Subcategory> findSubcategory(String code) {
        return Optional.ofNullable(snapshot().subcategories().get(code));
    }

    /**
     * Specific attributes of an active subcategory, in display order; empty when it only has the common fields.
     *
     * @throws CategoryNotFoundException for an unknown or inactive code, or a family code
     */
    public List<Attribute> attributesOf(String subcategoryCode) {
        Snapshot data = snapshot();
        if (!data.subcategories().containsKey(subcategoryCode)) {
            throw new CategoryNotFoundException(subcategoryCode);
        }
        return data.attributes().getOrDefault(subcategoryCode, List.of());
    }

    /** The 24 governorates, in display order. */
    public List<Governorate> governorates() {
        return snapshot().governorates();
    }

    public Optional<Governorate> findGovernorate(String code) {
        return snapshot().governorates().stream().filter(governorate -> governorate.code().equals(code)).findFirst();
    }

    /**
     * Delegations of a governorate, sorted by French label (the default language).
     *
     * @throws GovernorateNotFoundException for an unknown code
     */
    public List<Delegation> delegationsOf(String governorateCode) {
        Snapshot data = snapshot();
        if (findGovernorate(governorateCode).isEmpty()) {
            throw new GovernorateNotFoundException(governorateCode);
        }
        return data.delegationsByGovernorate().getOrDefault(governorateCode, List.of());
    }

    public Optional<Delegation> findDelegation(String code) {
        return Optional.ofNullable(snapshot().delegations().get(code));
    }

    private Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            synchronized (this) {
                current = snapshot;
                if (current == null) {
                    current = load();
                    snapshot = current;
                }
            }
        }
        return current;
    }

    private Snapshot load() {
        List<CategoryFamily> families = repository.activeCategories();
        Map<String, Subcategory> subcategories = families.stream()
                .flatMap(family -> family.subcategories().stream())
                .collect(Collectors.toUnmodifiableMap(Subcategory::code, Function.identity()));
        List<Delegation> delegations = repository.delegations();
        Collator french = Collator.getInstance(Locale.FRENCH);
        Map<String, List<Delegation>> byGovernorate = delegations.stream()
                .sorted(Comparator.comparing((Delegation delegation) -> delegation.label().fr(), french)
                        .thenComparing(Delegation::code))
                .collect(Collectors.groupingBy(Delegation::governorateCode,
                        Collectors.collectingAndThen(Collectors.toList(), List::copyOf)));
        return new Snapshot(families, subcategories, Map.copyOf(repository.attributesByCategory()),
                repository.governorates(), Map.copyOf(byGovernorate),
                delegations.stream().collect(Collectors.toUnmodifiableMap(Delegation::code, Function.identity())));
    }

    private record Snapshot(List<CategoryFamily> families, Map<String, Subcategory> subcategories,
            Map<String, List<Attribute>> attributes, List<Governorate> governorates,
            Map<String, List<Delegation>> delegationsByGovernorate, Map<String, Delegation> delegations) {
    }
}
