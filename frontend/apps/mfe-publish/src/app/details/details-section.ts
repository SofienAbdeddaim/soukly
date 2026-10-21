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
import {
  AbstractControl,
  FormControl,
  FormGroup,
  FormRecord,
  ReactiveFormsModule,
} from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocaleService } from '@soukly/i18n';
import {
  attributePrefix,
  AttributeControlKind,
  AttributeDefinition,
  controlKind,
  formatInteger,
  parseInteger,
  AttributeValue,
  labelOf,
  ListingPatch,
  ReferentialsApi,
} from '@soukly/listing-data';
import {
  UiAlert,
  UiButton,
  UiCheckbox,
  UiCheckboxControl,
  UiField,
  UiFieldControl,
  UiFieldPrefix,
  UiSkeleton,
  UiToggleGroup,
  UiToggleOption,
} from '@soukly/ui';
import { debounceTime } from 'rxjs';
import { DraftStore } from '../draft/draft-store';
import {
  AttributeInput,
  attributeValidator,
  descriptionValidator,
  DESCRIPTION_LENGTH,
  DetailsForm,
  ERROR_KEY,
  errorParams,
  fieldError,
  KNOWN_ERRORS,
  textToApi,
  titleValidator,
  toApi,
  toInput,
} from './details-form';

/** Delay after the last change before the valid fields are saved (ANN-04 hypothesis). */
export const AUTOSAVE_DELAY_MS = 1500;

interface AttributeView {
  definition: AttributeDefinition;
  kind: AttributeControlKind;
  label: string;
  prefix: string | null;
  options: UiToggleOption[];
  control: FormControl<AttributeInput>;
}

/**
 * Title, description, then the specific fields of the subcategory, generated from
 * `GET /categories/{code}/attributes` (CAT-01): no field is written here. Shown by step 3 of the
 * form (ANN-04) and by the edit page of a published listing (ANN-05). Valid fields go to the
 * `DraftStore` 1.5 s after the last change, and when the page calls `saveChanges()`: saved at once
 * for a draft, kept until "Save" on the edit page. The errors of the API are shown under their
 * field.
 */
@Component({
  selector: 'app-details-section',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    UiAlert,
    UiButton,
    UiCheckbox,
    UiCheckboxControl,
    UiField,
    UiFieldControl,
    UiFieldPrefix,
    UiSkeleton,
    UiToggleGroup,
  ],
  templateUrl: './details-section.html',
  styleUrls: ['../shared/step-page.css', './details-section.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetailsSection {
  /** The form was submitted with the Enter key. */
  readonly submitted = output();

  private readonly store = inject(DraftStore);
  private readonly referentials = inject(ReferentialsApi);
  private readonly locale = inject(LocaleService);
  private readonly injector = inject(Injector);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  protected readonly form: DetailsForm = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: titleValidator }),
    description: new FormControl('', { nonNullable: true, validators: descriptionValidator }),
    attributes: new FormRecord<FormControl<AttributeInput>>({}),
  });
  protected readonly descriptionMax = DESCRIPTION_LENGTH.max;

  protected readonly definitions = signal<AttributeDefinition[]>([]);
  protected readonly attributesState = signal<'loading' | 'ready' | 'error'>('loading');
  /** Changed on every event of the form: the template reads the state of the controls again. */
  private readonly formTick = signal(0);

  protected readonly attributes = computed<AttributeView[]>(() => {
    const lang = this.locale.lang();
    return this.definitions().map((definition) => ({
      definition,
      kind: controlKind(definition),
      label: labelOf(definition.label, lang),
      prefix: attributePrefix(definition.code),
      options: (definition.values ?? []).map((value) => ({
        value: value.code,
        label: labelOf(value.label, lang),
      })),
      control: this.form.controls.attributes.controls[definition.code],
    }));
  });

  /** Last values the API has, in its format, by field (`title`, `attributes.year`). */
  private saved = new Map<string, AttributeValue | null>();

  constructor() {
    this.form.events
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.formTick.update((tick) => tick + 1));
    this.form.valueChanges
      .pipe(debounceTime(AUTOSAVE_DELAY_MS), takeUntilDestroyed())
      .subscribe(() => this.saveChanges());

    // The draft is read again (conflict): the form starts again from it.
    effect(() => {
      this.store.revision();
      untracked(() => this.fill());
    });
    // Errors of the API, under their field.
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
    // Leaving the step (back link, browser) still saves what was typed.
    inject(DestroyRef).onDestroy(() => this.saveChanges());

    void this.loadAttributes();
  }

  /**
   * Translation key of the error of a field, once it was left or the member tried to continue;
   * null otherwise. A code the page does not know gets the general message.
   */
  protected errorKey(control: AbstractControl): string | null {
    this.formTick();
    const error = fieldError(control);
    if (!error || !control.touched) {
      return null;
    }
    return `publish.errors.${KNOWN_ERRORS.has(error.code) ? error.code : 'unexpected'}`;
  }

  /** Bounds of an attribute for its error message, numbers formatted as in the field. */
  protected errorParams(attribute: AttributeDefinition): Record<string, number | string> {
    const params = errorParams(attribute);
    if (attribute.type === 'INTEGER') {
      for (const bound of ['min', 'max'] as const) {
        const value = params[bound];
        if (typeof value === 'number') {
          params[bound] = formatInteger(value, attribute, this.locale.intlLocale());
        }
      }
    }
    return params;
  }

  protected descriptionLength(): number {
    this.formTick();
    return [...this.form.controls.description.value.trim()].length;
  }

  protected choose(view: AttributeView, value: string | null): void {
    view.control.setValue(value);
    view.control.markAsDirty();
    view.control.markAsTouched();
  }

  /** Thousands separators shown once the number field is left ("62 000"). */
  protected formatOnBlur(view: AttributeView): void {
    const number = parseInteger(String(view.control.value ?? ''));
    if (number !== null && !Number.isNaN(number)) {
      view.control.setValue(formatInteger(number, view.definition, this.locale.intlLocale()), {
        emitEvent: false,
      });
    }
  }

  protected async loadAttributes(): Promise<void> {
    const categoryCode = this.store.listing()?.categoryCode;
    if (!categoryCode) {
      return;
    }
    this.attributesState.set('loading');
    try {
      const definitions = await this.referentials.attributes(categoryCode);
      const record = this.form.controls.attributes;
      for (const definition of definitions) {
        record.setControl(
          definition.code,
          new FormControl<AttributeInput>(null, { validators: attributeValidator(definition) }),
          { emitEvent: false },
        );
      }
      this.definitions.set(definitions);
      this.attributesState.set('ready');
      this.fill();
    } catch {
      this.attributesState.set('error');
    }
  }

  /**
   * Shows every error; true when every field is valid and the specific fields are loaded,
   * otherwise the first error receives the focus.
   */
  validate(): boolean {
    this.form.markAllAsTouched();
    this.formTick.update((tick) => tick + 1);
    if (this.form.invalid || this.attributesState() !== 'ready') {
      this.focusFirstError();
      return false;
    }
    return true;
  }

  /** Whether a field differs from what the API has (typed and not given to the store yet). */
  hasChanges(): boolean {
    return this.form.dirty;
  }

  /** Values of the draft in the form; what the API has is the reference for the next saves. */
  private fill(): void {
    const listing = this.store.listing();
    if (!listing) {
      return;
    }
    const locale = this.locale.intlLocale();
    this.form.controls.title.setValue(listing.title ?? '', { emitEvent: false });
    this.form.controls.description.setValue(listing.description ?? '', { emitEvent: false });
    this.saved = new Map<string, AttributeValue | null>([
      ['title', listing.title],
      ['description', listing.description],
    ]);
    for (const definition of this.definitions()) {
      const input = toInput(definition, listing.attributes[definition.code], locale);
      this.form.controls.attributes.controls[definition.code]?.setValue(input, {
        emitEvent: false,
      });
      this.saved.set(`attributes.${definition.code}`, toApi(definition, input));
    }
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.formTick.update((tick) => tick + 1);
  }

  /** Gives the store the valid fields whose value differs from what the API has. */
  saveChanges(): void {
    if (!this.store.listing()) {
      return;
    }
    const patch: ListingPatch = {};
    const { title, description } = this.form.controls;
    const changed = (field: string, value: AttributeValue | null, control: AbstractControl) =>
      control.valid && control.dirty && this.saved.get(field) !== value;

    const titleValue = textToApi(title.value, false);
    if (changed('title', titleValue, title)) {
      patch.title = titleValue;
      this.saved.set('title', titleValue);
    }
    const descriptionValue = textToApi(description.value, true);
    if (changed('description', descriptionValue, description)) {
      patch.description = descriptionValue;
      this.saved.set('description', descriptionValue);
    }
    for (const definition of this.definitions()) {
      const control = this.form.controls.attributes.controls[definition.code];
      const field = `attributes.${definition.code}`;
      const value = control ? toApi(definition, control.value) : null;
      if (control && changed(field, value, control)) {
        patch.attributes = { ...patch.attributes, [definition.code]: value };
        this.saved.set(field, value);
      }
    }
    if (Object.keys(patch).length) {
      void this.store.save(patch);
    }
  }

  private controlOf(field: string): AbstractControl | null {
    if (field === 'title' || field === 'description') {
      return this.form.controls[field];
    }
    const code = field.startsWith('attributes.') ? field.slice('attributes.'.length) : null;
    return code ? (this.form.controls.attributes.controls[code] ?? null) : null;
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
