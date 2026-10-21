import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideChevronDown, LucideMapPin } from '@lucide/angular';
import { LocaleService } from '@soukly/i18n';
import { labelOf } from '@soukly/listing-data';
import { CatalogReferentials } from '../../shared/catalog-referentials';

/**
 * "All of Tunisia / {governorate}" pill of the home page: a native `select` (the list of the
 * phone on mobile), first option "All of Tunisia". Emits the code chosen, null for all of Tunisia.
 */
@Component({
  selector: 'app-governorate-picker',
  imports: [TranslocoPipe, LucideChevronDown, LucideMapPin],
  template: `
    <label class="ui-visually-hidden" for="home-governorate">
      {{ 'catalog.home.place-label' | transloco }}
    </label>
    <span class="picker">
      <svg lucideMapPin [size]="16" class="picker__icon" aria-hidden="true"></svg>
      <select
        id="home-governorate"
        class="picker__select"
        data-testid="home-governorate"
        (change)="choose($any($event.target).value)"
      >
        <option value="" [selected]="!value()">{{ 'catalog.place.everywhere' | transloco }}</option>
        @for (governorate of governorates(); track governorate.code) {
          <option [value]="governorate.code" [selected]="governorate.code === value()">
            {{ governorate.label }}
          </option>
        }
      </select>
      <svg lucideChevronDown [size]="16" class="picker__chevron" aria-hidden="true"></svg>
    </span>
  `,
  styleUrl: './governorate-picker.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GovernoratePicker {
  private readonly locale = inject(LocaleService);
  private readonly referentials = inject(CatalogReferentials);

  readonly value = input<string | null>(null);
  readonly valueChange = output<string | null>();

  protected readonly governorates = computed(() =>
    this.referentials.governorates().map((governorate) => ({
      code: governorate.code,
      label: labelOf(governorate.label, this.locale.lang()),
    })),
  );

  protected choose(code: string): void {
    this.valueChange.emit(code || null);
  }
}
