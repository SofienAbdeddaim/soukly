import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { currencySymbol, formatNumber, LocaleService } from '@soukly/i18n';
import {
  AttributeDefinition,
  familyOf,
  labelOf,
  parseInteger,
  usesGrouping,
  withoutUnit,
  attributeUnit,
} from '@soukly/listing-data';
import {
  UiCheckbox,
  UiCheckboxControl,
  UiField,
  UiFieldControl,
  UiToggleGroup,
  UiToggleOption,
} from '@soukly/ui';
import {
  isSubcategory,
  SearchCriteria,
  withCategory,
  withGovernorate,
} from '../search/search-criteria';
import { CatalogReferentials } from '../shared/catalog-referentials';

/** How a filterable attribute is filtered (CATA-02): a range, several values, a box, a text. */
export type FilterControlKind = 'range' | 'toggles' | 'checkbox' | 'text';

export function filterControlKind(attribute: AttributeDefinition): FilterControlKind {
  switch (attribute.type) {
    case 'INTEGER':
      return 'range';
    case 'ENUM':
      return 'toggles';
    case 'BOOLEAN':
      return 'checkbox';
    case 'TEXT':
      return 'text';
  }
}

interface AttributeFilterView {
  code: string;
  kind: FilterControlKind;
  label: string;
  unit: string | null;
  options: UiToggleOption[];
}

/**
 * Fields of the search filters, in the desktop column and in the mobile dialog: category (family
 * then subcategory), governorate then delegation, price, then the filterable attributes of the
 * subcategory, generated from CAT-01 like the fields of the publish form (ANN-04): no attribute
 * is written here. Controlled: it shows `criteria` and emits the criteria each change gives;
 * its host decides when they apply.
 */
@Component({
  selector: 'app-filters-form',
  imports: [TranslocoPipe, UiCheckbox, UiCheckboxControl, UiField, UiFieldControl, UiToggleGroup],
  templateUrl: './filters-form.html',
  styleUrl: './filters-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FiltersForm {
  private readonly locale = inject(LocaleService);
  private readonly referentials = inject(CatalogReferentials);

  readonly criteria = input.required<SearchCriteria>();
  /** Prefix of the ids of the fields: the column and the dialog can both be in the page. */
  readonly idPrefix = input('filters');
  readonly criteriaChange = output<SearchCriteria>();

  protected readonly currency = computed(() => currencySymbol(this.locale.intlLocale()));

  protected readonly families = computed(() =>
    this.referentials.families().map((family) => ({
      code: family.code,
      label: labelOf(family.label, this.locale.lang()),
    })),
  );

  protected readonly family = computed(() => {
    const category = this.criteria().category;
    return category ? (isSubcategory(category) ? familyOf(category) : category) : null;
  });

  protected readonly subcategories = computed(() => {
    const family = this.referentials.families().find((item) => item.code === this.family());
    return (family?.subcategories ?? []).map((subcategory) => ({
      code: subcategory.code,
      label: labelOf(subcategory.label, this.locale.lang()),
    }));
  });

  protected readonly governorates = computed(() =>
    this.referentials.governorates().map((governorate) => ({
      code: governorate.code,
      label: labelOf(governorate.label, this.locale.lang()),
    })),
  );

  protected readonly delegations = computed(() => {
    const governorate = this.criteria().governorate;
    return (governorate ? (this.referentials.delegations()[governorate] ?? []) : []).map(
      (delegation) => ({
        code: delegation.code,
        label: labelOf(delegation.label, this.locale.lang()),
      }),
    );
  });

  /** Specific filters, only once a subcategory is chosen (CATA-01). */
  protected readonly attributes = computed<AttributeFilterView[]>(() => {
    const category = this.criteria().category;
    if (!isSubcategory(category)) {
      return [];
    }
    const lang = this.locale.lang();
    return (this.referentials.attributes()[category] ?? [])
      .filter((attribute) => attribute.filterable)
      .map((attribute) => {
        const label = labelOf(attribute.label, lang);
        return {
          code: attribute.code,
          kind: filterControlKind(attribute),
          label: attribute.type === 'INTEGER' ? withoutUnit(label) : label,
          unit: attribute.type === 'INTEGER' ? attributeUnit(label) : null,
          options: (attribute.values ?? []).map((value) => ({
            value: value.code,
            label: labelOf(value.label, lang),
          })),
        };
      });
  });

  constructor() {
    void this.referentials.loadBasics();
    effect(() => {
      const { category, governorate } = this.criteria();
      if (isSubcategory(category)) {
        void this.referentials.loadAttributes(category);
      }
      if (governorate) {
        void this.referentials.loadDelegations(governorate);
      }
    });
  }

  protected id(name: string): string {
    return `${this.idPrefix()}-${name}`;
  }

  protected chooseFamily(code: string): void {
    void this.chooseCategory(code || null);
  }

  protected chooseSubcategory(code: string): void {
    void this.chooseCategory(code || this.family());
  }

  protected chooseGovernorate(code: string): void {
    this.criteriaChange.emit(withGovernorate(this.criteria(), code || null));
  }

  protected chooseDelegation(code: string): void {
    this.criteriaChange.emit({ ...this.criteria(), delegation: code || null });
  }

  protected setPrice(bound: 'priceMin' | 'priceMax', event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = wholeNumber(input.value);
    input.value = value === null ? '' : formatNumber(value, this.locale.intlLocale());
    this.criteriaChange.emit({ ...this.criteria(), [bound]: value });
  }

  protected priceText(value: number | null): string {
    return value === null ? '' : formatNumber(value, this.locale.intlLocale());
  }

  protected attribute(name: string): string | null {
    return this.criteria().attributes[name] ?? null;
  }

  protected rangeText(name: string): string {
    const value = this.attribute(name);
    const definition = this.definition(name.replace(/\.(min|max)$/u, ''));
    if (value === null || !definition || !usesGrouping(definition)) {
      return value ?? '';
    }
    return formatNumber(Number(value), this.locale.intlLocale());
  }

  protected setRange(name: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = wholeNumber(input.value);
    if (value === null) {
      input.value = '';
    }
    this.setAttribute(name, value === null ? null : String(value));
  }

  protected enumValues(code: string): string[] {
    return (this.attribute(code) ?? '').split(',').filter(Boolean);
  }

  protected setEnum(code: string, values: string[]): void {
    this.setAttribute(code, values.length ? values.join(',') : null);
  }

  protected setFlag(code: string, event: Event): void {
    this.setAttribute(code, (event.target as HTMLInputElement).checked ? 'true' : null);
  }

  protected setText(code: string, event: Event): void {
    this.setAttribute(code, (event.target as HTMLInputElement).value.trim() || null);
  }

  private setAttribute(name: string, value: string | null): void {
    const attributes = { ...this.criteria().attributes };
    if (value === null) {
      delete attributes[name];
    } else {
      attributes[name] = value;
    }
    this.criteriaChange.emit({ ...this.criteria(), attributes });
  }

  private definition(code: string): AttributeDefinition | undefined {
    const category = this.criteria().category;
    return category
      ? this.referentials.attributes()[category]?.find((attribute) => attribute.code === code)
      : undefined;
  }

  /** A new category keeps the specific filters its subcategory also has. */
  private async chooseCategory(category: string | null): Promise<void> {
    let kept: string[] = [];
    if (isSubcategory(category)) {
      await this.referentials.loadAttributes(category);
      kept = (this.referentials.attributes()[category] ?? [])
        .filter((attribute) => attribute.filterable)
        .map((attribute) => attribute.code);
    }
    this.criteriaChange.emit(withCategory(this.criteria(), category, kept));
  }
}

/** A whole number typed with or without separators; anything else is null. */
function wholeNumber(text: string): number | null {
  const value = parseInteger(text);
  return value === null || Number.isNaN(value) || value < 0 ? null : value;
}
