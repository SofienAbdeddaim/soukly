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
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LocaleService } from '@soukly/i18n';
import {
  attributePrefix,
  AttributeDefinition,
  AttributeValue,
  labelOf,
  ListingPatch,
  MAX_TOGGLE_VALUES,
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
import { StepLayout } from '../layout/step-layout/step-layout';
import { stepCommands } from '../shared/publish-steps';
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
  formatInteger,
  parseInteger,
  textToApi,
  titleValidator,
  toApi,
  toInput,
} from './details-form';

/** Delay after the last change before the valid fields are saved (ANN-04 hypothesis). */
export const AUTOSAVE_DELAY_MS = 1500;

/** How an attribute is rendered, from its type and number of values only. */
export type AttributeControlKind = 'text' | 'integer' | 'toggles' | 'select' | 'checkbox';

export function controlKind(attribute: AttributeDefinition): AttributeControlKind {
  switch (attribute.type) {
    case 'TEXT':
      return 'text';
    case 'INTEGER':
      return 'integer';
    case 'BOOLEAN':
      return 'checkbox';
    case 'ENUM':
      return (attribute.values?.length ?? 0) <= MAX_TOGGLE_VALUES ? 'toggles' : 'select';
  }
}

interface AttributeView {
  definition: AttributeDefinition;
  kind: AttributeControlKind;
  label: string;
  prefix: string | null;
  options: UiToggleOption[];
  control: FormControl<AttributeInput>;
}

/**
 * Step 3 (`/{lang}/publish/{id}/details`): title, description, then the specific fields of the
 * subcategory, generated from `GET /categories/{code}/attributes` (CAT-01): no field is written
 * here. Valid fields are saved 1.5 s after the last change and when the member continues; the
 * errors of the API are shown under their field.
 */
@Component({
  selector: 'app-details-step',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    StepLayout,
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
  templateUrl: './details-step.html',
  styleUrls: ['../shared/step-page.css', './details-step.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetailsStep {
  private readonly store = inject(DraftStore);
  private readonly referentials = inject(ReferentialsApi);
  private readonly locale = inject(LocaleService);
  private readonly router = inject(Router);
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
  protected readonly saving = signal(false);
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

  protected async next(): Promise<void> {
    this.form.markAllAsTouched();
    this.formTick.update((tick) => tick + 1);
    if (this.form.invalid || this.attributesState() !== 'ready') {
      this.focusFirstError();
      return;
    }
    this.saving.set(true);
    this.saveChanges();
    const saved = await this.store.flush();
    this.saving.set(false);
    const id = this.store.id();
    if (saved && id) {
      await this.router.navigate(stepCommands(this.locale.lang(), id, 'price-location'));
    } else {
      this.focusFirstError();
    }
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

  /** Sends the valid fields whose value differs from what the API has. */
  private saveChanges(): void {
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

  private focusFirstError(): void {
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
