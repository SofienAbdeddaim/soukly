import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocaleService } from '@soukly/i18n';
import { CategoryFamily, familyOf, labelOf, ReferentialsApi } from '@soukly/listing-data';
import { UiAlert, UiButton, UiSkeleton, UiToggleGroup, UiToggleOption } from '@soukly/ui';

/**
 * Choice of the subcategory (step 1): the 10 families as tiles, then the subcategories of the
 * chosen family as pills, labelled in the language of the page (CAT-01). Emits the code of the
 * chosen subcategory; the page creates or updates the draft.
 */
@Component({
  selector: 'app-category-picker',
  imports: [TranslocoPipe, UiAlert, UiButton, UiSkeleton, UiToggleGroup],
  template: `
    <h2 class="step__title">{{ 'publish.category.title' | transloco }}</h2>
    @switch (state()) {
      @case ('loading') {
        <div class="picker__loading" aria-busy="true" data-testid="categories-loading">
          @for (index of placeholders; track index) {
            <ui-skeleton variant="block" blockSize="56px" />
          }
        </div>
      }
      @case ('error') {
        <ui-alert type="error">{{ 'publish.category.load-error' | transloco }}</ui-alert>
        <button ui-button variant="secondary" type="button" (click)="load()">
          {{ 'publish.actions.retry' | transloco }}
        </button>
      }
      @default {
        <ui-toggle-group
          layout="tiles"
          hideLegend
          required
          data-testid="families"
          [legend]="'publish.category.families' | transloco"
          [options]="familyOptions()"
          [value]="family()"
          [disabled]="busy()"
          (valueChange)="family.set($event)"
        />
        @if (subcategoryOptions(); as options) {
          <ui-toggle-group
            required
            data-testid="subcategories"
            [legend]="familyLabel()"
            [options]="options"
            [value]="selected()"
            [disabled]="busy()"
            (valueChange)="choose($event)"
          />
        }
      }
    }
  `,
  styleUrl: '../shared/step-page.css',
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .picker__loading {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoryPicker {
  /** Subcategory of the draft, if any. */
  readonly selected = input<string | null>(null);
  /** While the draft is created or saved: the choices are disabled. */
  readonly busy = input(false);
  readonly chosen = output<string>();

  private readonly referentials = inject(ReferentialsApi);
  private readonly locale = inject(LocaleService);

  protected readonly placeholders = Array.from({ length: 10 }, (_, index) => index);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  private readonly families = signal<CategoryFamily[]>([]);
  protected readonly family = signal<string | null>(null);

  protected readonly familyOptions = computed<UiToggleOption[]>(() =>
    this.families().map((family) => ({
      value: family.code,
      label: labelOf(family.label, this.locale.lang()),
    })),
  );
  private readonly currentFamily = computed(() =>
    this.families().find((family) => family.code === this.family()),
  );
  protected readonly familyLabel = computed(() => {
    const family = this.currentFamily();
    return family ? labelOf(family.label, this.locale.lang()) : '';
  });
  protected readonly subcategoryOptions = computed<UiToggleOption[] | null>(
    () =>
      this.currentFamily()?.subcategories.map((subcategory) => ({
        value: subcategory.code,
        label: labelOf(subcategory.label, this.locale.lang()),
      })) ?? null,
  );

  constructor() {
    // The family of the draft is open when the step is shown again.
    effect(() => {
      const selected = this.selected();
      if (selected) {
        this.family.set(familyOf(selected));
      }
    });
    void this.load();
  }

  protected async load(): Promise<void> {
    this.state.set('loading');
    try {
      this.families.set(await this.referentials.categories());
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  protected choose(code: string | null): void {
    if (code && code !== this.selected()) {
      this.chosen.emit(code);
    }
  }
}
