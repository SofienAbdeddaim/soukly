import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  Injector,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideArrowLeft, LucideSend } from '@lucide/angular';
import { problemCode } from '@soukly/auth';
import { formatPrice, LocalePathPipe, LocaleService } from '@soukly/i18n';
import {
  attributePrefix,
  AttributeDefinition,
  AttributeValue,
  Delegation,
  formatInteger,
  Governorate,
  labelOf,
  ListingsApi,
  ReferentialsApi,
} from '@soukly/listing-data';
import { UiAlert, UiButton } from '@soukly/ui';
import { DraftStore } from '../draft/draft-store';
import { SaveStatus } from '../layout/save-status/save-status';
import { StepLayout } from '../layout/step-layout/step-layout';
import { mediaQuery } from '../shared/media-query';
import { PublishStep, stepCommands, stepOfField } from '../shared/publish-steps';
import { AttributeLine, PreviewCard, PreviewEditLinks } from './preview-card';
import { PublicationFacts } from './publication-facts';
import { ShareBlock } from './share-block';

/** Members of `missingFields` with their own label; `attributes.<code>` take the attribute's. */
const FIELD_LABELS = new Set([
  'categoryCode',
  'photos',
  'title',
  'description',
  'priceMode',
  'price',
  'governorateCode',
]);

interface MissingField {
  field: string;
  step: PublishStep;
  /** Label of an attribute, from the referentials. */
  label: string | null;
}

/**
 * Step 5 (`/{lang}/publish/{id}/preview`): the listing as it will look, the elements still
 * missing (each a link to its step, publishing disabled meanwhile), where to share it (SOC-03),
 * how long it stays online, then "Publish". From 1024px the step is laid out in two columns: the
 * card, and a side column with sharing and the actions, so there is no bottom bar. A success
 * leads to the confirmation page; `422 listing-incomplete` reads the draft again, so the list of
 * missing elements is up to date.
 */
@Component({
  selector: 'app-preview-step',
  imports: [
    NgTemplateOutlet,
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    LucideArrowLeft,
    LucideSend,
    StepLayout,
    SaveStatus,
    UiAlert,
    UiButton,
    PreviewCard,
    PublicationFacts,
    ShareBlock,
  ],
  templateUrl: './preview-step.html',
  styleUrls: ['../shared/step-page.css', './preview-step.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PreviewStep {
  protected readonly store = inject(DraftStore);
  private readonly listings = inject(ListingsApi);
  private readonly referentials = inject(ReferentialsApi);
  private readonly locale = inject(LocaleService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly missingAlert = viewChild<UiAlert>('missingAlert');

  private readonly definitions = signal<AttributeDefinition[]>([]);
  private readonly governorateList = signal<Governorate[]>([]);
  private readonly delegationList = signal<Delegation[]>([]);
  protected readonly publishing = signal(false);
  protected readonly failed = signal(false);

  protected readonly listing = this.store.listing;
  /** Two columns, actions in the side column (same breakpoint as the CSS). */
  protected readonly wide = mediaQuery('(min-width: 1024px)');

  protected readonly editLinks = computed<PreviewEditLinks>(() => ({
    photos: this.stepLink('photos'),
    details: this.stepLink('details'),
    priceLocation: this.stepLink('price-location'),
  }));

  /** "32 000 DT", or null when the listing is "on quote" or has no price yet. */
  protected readonly price = computed(() => {
    const listing = this.listing();
    return listing?.priceMode !== 'ON_QUOTE' && listing?.price != null
      ? formatPrice(listing.price, this.locale.intlLocale())
      : null;
  });

  /** Labels of the governorate and of the delegation, when chosen. */
  protected readonly place = computed(() => {
    const listing = this.listing();
    const lang = this.locale.lang();
    const governorate = this.governorateList().find(
      (candidate) => candidate.code === listing?.governorateCode,
    );
    const delegation = this.delegationList().find(
      (candidate) => candidate.code === listing?.delegationCode,
    );
    return governorate
      ? {
          governorate: labelOf(governorate.label, lang),
          delegation: delegation ? labelOf(delegation.label, lang) : null,
        }
      : null;
  });

  /** Specific fields filled in, in the order of the subcategory. */
  protected readonly attributes = computed<AttributeLine[]>(() => {
    const listing = this.listing();
    const lang = this.locale.lang();
    if (!listing) {
      return [];
    }
    return this.definitions()
      .filter((definition) => listing.attributes[definition.code] != null)
      .map((definition) => {
        const value = listing.attributes[definition.code];
        return {
          code: definition.code,
          label: labelOf(definition.label, lang),
          value: definition.type === 'BOOLEAN' ? null : this.valueText(definition, value),
          checked: value === true,
        };
      });
  });

  protected readonly missing = computed<MissingField[]>(() => {
    const lang = this.locale.lang();
    return (this.listing()?.missingFields ?? []).map((field) => {
      const code = field.startsWith('attributes.') ? field.slice('attributes.'.length) : null;
      const definition = this.definitions().find((candidate) => candidate.code === code);
      return {
        field,
        step: stepOfField(field),
        label: definition ? labelOf(definition.label, lang) : null,
      };
    });
  });

  constructor() {
    // The listing is read again after a refused publication: its missing elements are shown.
    effect(() => {
      const listing = this.store.listing();
      untracked(() => void this.loadReferentials(listing?.categoryCode, listing?.governorateCode));
    });
  }

  protected fieldKey(field: string): string {
    return `publish.preview.fields.${FIELD_LABELS.has(field) ? field : 'attribute'}`;
  }

  protected stepLink(step: PublishStep): string[] {
    return stepCommands(this.locale.lang(), this.store.id() ?? '', step);
  }

  protected async publish(): Promise<void> {
    const id = this.store.id();
    if (!id || this.missing().length || this.publishing()) {
      return;
    }
    this.failed.set(false);
    this.publishing.set(true);
    try {
      if (!(await this.store.flush())) {
        return;
      }
      await this.listings.publish(id);
      await this.router.navigate(stepCommands(this.locale.lang(), id, 'done'), {
        replaceUrl: true,
      });
    } catch (error) {
      const code = problemCode(error);
      if (code === 'listing-incomplete') {
        await this.store.reload();
        afterNextRender(() => this.missingAlert()?.focus(), { injector: this.injector });
      } else if (code === 'already-published') {
        await this.router.navigate(stepCommands(this.locale.lang(), id, 'done'), {
          replaceUrl: true,
        });
      } else {
        this.failed.set(true);
      }
    } finally {
      this.publishing.set(false);
    }
  }

  private valueText(definition: AttributeDefinition, value: AttributeValue): string {
    const lang = this.locale.lang();
    let text: string;
    if (definition.type === 'ENUM') {
      const option = definition.values?.find((candidate) => candidate.code === value);
      text = option ? labelOf(option.label, lang) : String(value);
    } else if (definition.type === 'INTEGER' && typeof value === 'number') {
      text = formatInteger(value, definition, this.locale.intlLocale());
    } else {
      text = String(value);
    }
    return `${attributePrefix(definition.code) ?? ''}${text}`;
  }

  /** Labels of the attributes and of the place; the preview stays usable without them. */
  private async loadReferentials(
    categoryCode: string | undefined,
    governorateCode: string | null | undefined,
  ): Promise<void> {
    const [definitions, governorates, delegations] = await Promise.all([
      categoryCode ? this.referentials.attributes(categoryCode).catch(() => []) : [],
      this.referentials.governorates().catch(() => []),
      governorateCode ? this.referentials.delegations(governorateCode).catch(() => []) : [],
    ]);
    this.definitions.set(definitions);
    this.governorateList.set(governorates);
    this.delegationList.set(delegations);
  }
}
