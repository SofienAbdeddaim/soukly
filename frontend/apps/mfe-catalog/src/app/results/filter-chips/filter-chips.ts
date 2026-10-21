import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideX } from '@lucide/angular';
import { formatNumber, formatPrice, LocaleService } from '@soukly/i18n';
import { AttributeDefinition, formatInteger, labelOf, withoutUnit } from '@soukly/listing-data';
import { ActiveFilter } from '../../search/search-criteria';
import { CatalogReferentials } from '../../shared/catalog-referentials';

/** Text of a chip: already in the language of the page, or a key of the `catalog` scope. */
interface Chip {
  filter: ActiveFilter;
  text: string | null;
  key: string | null;
  params: Record<string, string>;
}

/**
 * Chips of the active filters, each a button that removes its filter ("Remove the filter Diesel").
 * Labels come from the referentials; a filter whose label is not loaded yet shows its code.
 */
@Component({
  selector: 'app-filter-chips',
  imports: [TranslocoPipe, LucideX],
  template: `
    <ul class="chips" data-testid="filter-chips">
      @for (chip of chips(); track $index) {
        @let label = chip.text ?? (chip.key ?? '' | transloco: chip.params);
        <li>
          <button
            type="button"
            class="chips__chip"
            [attr.aria-label]="'catalog.chips.remove' | transloco: { label }"
            (click)="remove.emit(chip.filter)"
          >
            <span>{{ label }}</span>
            <svg lucideX [size]="14" [strokeWidth]="2.4" aria-hidden="true"></svg>
          </button>
        </li>
      }
    </ul>
  `,
  styleUrl: './filter-chips.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilterChips {
  private readonly locale = inject(LocaleService);
  private readonly referentials = inject(CatalogReferentials);

  readonly filters = input.required<readonly ActiveFilter[]>();
  /** Subcategory of the search, whose attributes name the specific filters. */
  readonly category = input<string | null>(null);
  readonly remove = output<ActiveFilter>();

  protected readonly chips = computed(() => this.filters().map((filter) => this.chip(filter)));

  private chip(filter: ActiveFilter): Chip {
    const lang = this.locale.lang();
    const locale = this.locale.intlLocale();
    const text = (value: string): Chip => ({ filter, text: value, key: null, params: {} });
    const translated = (key: string, params: Record<string, string>): Chip => ({
      filter,
      text: null,
      key,
      params,
    });
    switch (filter.kind) {
      case 'category': {
        const subcategory = this.referentials.subcategories().get(filter.code);
        const family = this.referentials.families().find((item) => item.code === filter.code);
        const labels = subcategory?.label ?? family?.label;
        return text(labels ? labelOf(labels, lang) : filter.code);
      }
      case 'governorate': {
        const governorate = this.referentials
          .governorates()
          .find((item) => item.code === filter.code);
        return text(governorate ? labelOf(governorate.label, lang) : filter.code);
      }
      case 'delegation': {
        const governorate = filter.code.split('.')[0];
        const delegation = this.referentials
          .delegations()
          [governorate]?.find((item) => item.code === filter.code);
        return text(delegation ? labelOf(delegation.label, lang) : filter.code);
      }
      case 'price': {
        const min = filter.min === null ? null : formatPrice(filter.min, locale);
        const max = filter.max === null ? null : formatPrice(filter.max, locale);
        return boundsChip(translated, 'catalog.chips.price', '', min, max);
      }
      case 'range': {
        const definition = this.definition(filter.code);
        const label = definition ? withoutUnit(labelOf(definition.label, lang)) : filter.code;
        const number = (value: string | null) =>
          value === null
            ? null
            : definition
              ? formatInteger(Number(value), definition, locale)
              : formatNumber(Number(value), locale);
        return boundsChip(
          translated,
          'catalog.chips.range',
          label,
          number(filter.min),
          number(filter.max),
        );
      }
      case 'enum': {
        const value = this.definition(filter.code)?.values?.find(
          (item) => item.code === filter.value,
        );
        return text(value ? labelOf(value.label, lang) : filter.value);
      }
      case 'flag': {
        const definition = this.definition(filter.code);
        const label = definition ? labelOf(definition.label, lang) : filter.code;
        return filter.value === 'true' ? text(label) : translated('catalog.chips.not', { label });
      }
      case 'text': {
        const definition = this.definition(filter.code);
        const label = definition ? labelOf(definition.label, lang) : filter.code;
        return translated('catalog.chips.text', { label, value: filter.value });
      }
    }
  }

  private definition(code: string): AttributeDefinition | undefined {
    const category = this.category();
    return category
      ? this.referentials.attributes()[category]?.find((item) => item.code === code)
      : undefined;
  }
}

/** "10 000 – 40 000 DT", "From 10 000 DT", "Up to 40 000 DT", with the name of a range. */
function boundsChip(
  translated: (key: string, params: Record<string, string>) => Chip,
  base: string,
  label: string,
  min: string | null,
  max: string | null,
): Chip {
  if (min !== null && max !== null) {
    return translated(`${base}-between`, { label, min, max });
  }
  return min !== null
    ? translated(`${base}-from`, { label, min })
    : translated(`${base}-up-to`, { label, max: max ?? '' });
}
