import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { currencySymbol, formatNumber, LocaleService } from '@soukly/i18n';
import {
  Delegation,
  Governorate,
  labelOf,
  ListingPatch,
  parseInteger,
  PriceMode,
  ReferentialsApi,
} from '@soukly/listing-data';
import {
  UiAlert,
  UiButton,
  UiCheckbox,
  UiCheckboxControl,
  UiField,
  UiFieldControl,
  UiFieldUnit,
  UiToggleGroup,
} from '@soukly/ui';
import { debounceTime } from 'rxjs';
import { ERROR_KEY, fieldError } from '../details/details-form';
import { AUTOSAVE_DELAY_MS } from '../details/details-section';
import { DraftStore } from '../draft/draft-store';
import {
  governorateValidator,
  PRICE_LOCATION_ERRORS,
  PRICE_RANGE,
  PriceLocationForm,
  PriceLocationValues,
  priceModeValidator,
  priceValidator,
  toApiValues,
} from './price-location-form';

interface PlaceOption {
  code: string;
  label: string;
}

/**
 * Price and place, shown by step 4 of the form (ANN-04) and by the edit page of a published
 * listing (ANN-05): the price in whole dinars, typed left to right
 * with the symbol of the language ("DT", "د.ت."), and the place. Services and jobs may be priced
 * "on quote" instead (ANN-01: only listings of kind SERVICE); a sale always has a price. The
 * delegation is optional and loaded for the chosen governorate. Given to the store like the
 * details: valid fields 1.5 s after the last change, and when the page calls `saveChanges()`.
 */
@Component({
  selector: 'app-price-location-section',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    UiAlert,
    UiButton,
    UiCheckbox,
    UiCheckboxControl,
    UiField,
    UiFieldControl,
    UiFieldUnit,
    UiToggleGroup,
  ],
  templateUrl: './price-location-section.html',
  styleUrls: ['../shared/step-page.css', './price-location-section.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriceLocationSection {
  /** The form was submitted with the Enter key. */
  readonly submitted = output();

  private readonly store = inject(DraftStore);
  private readonly referentials = inject(ReferentialsApi);
  private readonly locale = inject(LocaleService);
  private readonly injector = inject(Injector);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  protected readonly form: PriceLocationForm = new FormGroup({
    priceMode: new FormControl<PriceMode | null>(null, { validators: priceModeValidator }),
    price: new FormControl('', { nonNullable: true, validators: priceValidator }),
    negotiable: new FormControl(false, { nonNullable: true }),
    governorateCode: new FormControl<string | null>(null, { validators: governorateValidator }),
    delegationCode: new FormControl<string | null>(null),
  });

  /** Services and jobs choose between a price and "on quote"; a sale always has a price. */
  protected readonly quoteAllowed = computed(() => this.store.listing()?.kind === 'SERVICE');
  protected readonly currency = computed(() => currencySymbol(this.locale.intlLocale()));

  private readonly governorateList = signal<Governorate[]>([]);
  private readonly delegationList = signal<Delegation[]>([]);
  protected readonly governoratesState = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly delegationsState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  protected readonly governorates = computed(() => this.options(this.governorateList()));
  protected readonly delegations = computed(() => this.options(this.delegationList()));
  /** Changed on every event of the form: the template reads the state of the controls again. */
  private readonly formTick = signal(0);

  /** Last values the API has. */
  private saved: PriceLocationValues | null = null;

  constructor() {
    this.form.events
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.formTick.update((tick) => tick + 1));
    this.form.valueChanges
      .pipe(debounceTime(AUTOSAVE_DELAY_MS), takeUntilDestroyed())
      .subscribe(() => this.saveChanges());
    // Another governorate: its delegations, and the delegation chosen before is cleared.
    this.form.controls.governorateCode.valueChanges.pipe(takeUntilDestroyed()).subscribe((code) => {
      this.form.controls.delegationCode.setValue(null);
      void this.loadDelegations(code);
    });

    effect(() => {
      this.store.revision();
      untracked(() => this.fill());
    });
    effect(() => {
      const errors = this.store.fieldErrors();
      untracked(() => {
        for (const [field, code] of Object.entries(errors)) {
          const control = this.controlOf(field);
          control?.setErrors({ [ERROR_KEY]: { code } });
          control?.markAsTouched();
        }
        this.formTick.update((tick) => tick + 1);
      });
    });
    inject(DestroyRef).onDestroy(() => this.saveChanges());

    void this.loadGovernorates();
  }

  protected priceMode(): PriceMode | null {
    this.formTick();
    return this.form.controls.priceMode.value;
  }

  /** Translation key of the error of a field once it was left, null otherwise. */
  protected errorKey(control: AbstractControl): string | null {
    this.formTick();
    const error = fieldError(control);
    if (!error || !control.touched) {
      return null;
    }
    return `publish.errors.${PRICE_LOCATION_ERRORS.has(error.code) ? error.code : 'unexpected'}`;
  }

  protected readonly priceBounds = computed(() => ({
    min: formatNumber(PRICE_RANGE.min, this.locale.intlLocale()),
    max: formatNumber(PRICE_RANGE.max, this.locale.intlLocale()),
  }));

  protected chooseMode(mode: string | null): void {
    const control = this.form.controls.priceMode;
    control.setValue(mode as PriceMode | null);
    control.markAsDirty();
    control.markAsTouched();
    this.form.controls.price.updateValueAndValidity();
  }

  /** Thousands separators shown once the price field is left ("32 000"). */
  protected formatPrice(): void {
    const control = this.form.controls.price;
    const price = parseInteger(control.value);
    if (price !== null && !Number.isNaN(price)) {
      control.setValue(formatNumber(price, this.locale.intlLocale()), { emitEvent: false });
    }
  }

  protected async loadGovernorates(): Promise<void> {
    this.governoratesState.set('loading');
    try {
      this.governorateList.set(await this.referentials.governorates());
      this.governoratesState.set('ready');
    } catch {
      this.governoratesState.set('error');
    }
  }

  /** Shows every error; true when every field is valid, otherwise the first error is focused. */
  validate(): boolean {
    this.form.markAllAsTouched();
    this.formTick.update((tick) => tick + 1);
    if (this.form.invalid) {
      this.focusFirstError();
      return false;
    }
    return true;
  }

  /** Whether a field differs from what the API has (typed and not given to the store yet). */
  hasChanges(): boolean {
    return this.form.dirty;
  }

  private async loadDelegations(governorateCode: string | null): Promise<void> {
    this.delegationList.set([]);
    if (!governorateCode) {
      this.delegationsState.set('idle');
      return;
    }
    this.delegationsState.set('loading');
    try {
      const delegations = await this.referentials.delegations(governorateCode);
      if (governorateCode === this.form.controls.governorateCode.value) {
        this.delegationList.set(delegations);
        this.delegationsState.set('ready');
      }
    } catch {
      this.delegationsState.set('error');
    }
  }

  /** Values of the draft in the form; a sale gets a fixed price, the only mode it allows. */
  private fill(): void {
    const listing = this.store.listing();
    if (!listing) {
      return;
    }
    const locale = this.locale.intlLocale();
    const { priceMode, price, negotiable, governorateCode, delegationCode } = this.form.controls;
    priceMode.setValue(listing.kind === 'SERVICE' ? listing.priceMode : 'FIXED', {
      emitEvent: false,
    });
    price.setValue(listing.price === null ? '' : formatNumber(listing.price, locale), {
      emitEvent: false,
    });
    negotiable.setValue(listing.negotiable, { emitEvent: false });
    governorateCode.setValue(listing.governorateCode, { emitEvent: false });
    delegationCode.setValue(listing.delegationCode, { emitEvent: false });
    this.form.updateValueAndValidity({ emitEvent: false });
    this.saved = {
      priceMode: listing.priceMode,
      price: listing.price,
      negotiable: listing.negotiable,
      governorateCode: listing.governorateCode,
      delegationCode: listing.delegationCode,
    };
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.formTick.update((tick) => tick + 1);
    void this.loadDelegations(listing.governorateCode);
  }

  /** Gives the store the valid values that differ from what the API has. */
  saveChanges(): void {
    const saved = this.saved;
    if (!saved || !this.store.listing()) {
      return;
    }
    const values = toApiValues(this.form);
    const patch: ListingPatch = {};
    for (const key of Object.keys(values) as (keyof PriceLocationValues)[]) {
      const value = values[key];
      if (value !== undefined && value !== saved[key]) {
        Object.assign(patch, { [key]: value });
        Object.assign(saved, { [key]: value });
      }
    }
    if (Object.keys(patch).length) {
      void this.store.save(patch);
    }
  }

  private controlOf(field: string): AbstractControl | null {
    const controls = this.form.controls as Record<string, AbstractControl>;
    return controls[field] ?? null;
  }

  /** Labels in the language of the page, in the order of the referentials (CAT-01). */
  private options(places: readonly (Governorate | Delegation)[]): PlaceOption[] {
    const lang = this.locale.lang();
    return places.map((place) => ({ code: place.code, label: labelOf(place.label, lang) }));
  }

  focusFirstError(): void {
    afterNextRender(
      () =>
        this.element
          .querySelector<HTMLElement>(
            '[aria-invalid="true"], ui-toggle-group fieldset[aria-describedby] button',
          )
          ?.focus(),
      { injector: this.injector },
    );
  }
}
