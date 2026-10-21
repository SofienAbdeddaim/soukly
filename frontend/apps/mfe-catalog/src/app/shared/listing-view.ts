import { inject, Injectable } from '@angular/core';
import { formatPrice, formatRelativeTime, Lang, LocaleService, localizePath } from '@soukly/i18n';
import {
  attributePrefix,
  AttributeDefinition,
  AttributeValue,
  attributeUnit,
  Delegation,
  formatInteger,
  Governorate,
  labelOf,
  priceUnit,
  SearchCard,
} from '@soukly/listing-data';
import { CatalogReferentials } from './catalog-referentials';
import { pluralKey } from './plural';

/**
 * A search result ready for `ui-listing-row` and `ui-listing-card`: every text formatted in the
 * language of the page, except the translated ones, given as keys of the `catalog` scope (a remote
 * translates in its templates only, once its scope is loaded).
 */
export interface ListingView {
  id: string;
  href: string;
  title: string;
  /** Formatted price, null for a price on quote (`catalog.card.on-quote`). */
  price: string | null;
  /** Key of the price unit: " / mois", " / nuit". */
  unitKey: string | null;
  summary: string;
  place: string;
  dateLabel: string;
  dateTime: string;
  imageUrl: string | null;
  photoCount: number;
  /** Key of the accessible count of photos, with `count`. */
  photoKey: string;
}

/**
 * Builds the views of the results from the cards of CATA-01 and the referentials of CAT-01. No
 * data of the seller: the card has none.
 */
@Injectable({ providedIn: 'root' })
export class ListingViews {
  private readonly locale = inject(LocaleService);
  private readonly referentials = inject(CatalogReferentials);

  /** Asks for the labels the cards need: attributes of their subcategories, delegations. */
  prepare(cards: readonly SearchCard[]): void {
    for (const code of new Set(cards.map((card) => card.categoryCode))) {
      void this.referentials.loadAttributes(code);
    }
    for (const code of new Set(
      cards.filter((card) => card.delegationCode).map((card) => card.governorateCode),
    )) {
      void this.referentials.loadDelegations(code);
    }
  }

  view(card: SearchCard): ListingView {
    const lang = this.locale.lang();
    const locale = this.locale.intlLocale();
    const unit = priceUnit(card.categoryCode, card.summary);
    return {
      id: card.id,
      href: localizePath(`/listings/${encodeURIComponent(card.id)}`, lang),
      title: card.title,
      price:
        card.priceMode === 'ON_QUOTE' || card.price === null
          ? null
          : formatPrice(card.price, locale),
      unitKey: unit ? `catalog.card.per-${unit}` : null,
      summary: this.summary(card, lang, locale),
      place: this.place(card, lang),
      dateLabel: formatRelativeTime(card.publishedAt, locale),
      dateTime: card.publishedAt,
      imageUrl: card.coverThumbnailUrl,
      photoCount: card.photoCount,
      photoKey: pluralKey('catalog.card.photos', card.photoCount, locale),
    };
  }

  /** "2019 · 62 000 km · Diesel"; the subcategory when the listing has no specific attribute. */
  private summary(card: SearchCard, lang: Lang, locale: string): string {
    const definitions = this.referentials.attributes()[card.categoryCode] ?? [];
    const parts: string[] = [];
    for (const [code, value] of Object.entries(card.summary)) {
      const definition = definitions.find((candidate) => candidate.code === code);
      const text = definition ? valueText(definition, value, lang, locale) : null;
      if (text) {
        parts.push(text);
      }
    }
    if (parts.length) {
      return parts.join(' · ');
    }
    const subcategory = this.referentials.subcategories().get(card.categoryCode);
    return subcategory ? labelOf(subcategory.label, lang) : '';
  }

  /** "Sfax, Sakiet Ezzit", or the governorate alone. */
  private place(card: SearchCard, lang: Lang): string {
    const governorate = this.referentials
      .governorates()
      .find((candidate) => candidate.code === card.governorateCode);
    const delegation = card.delegationCode
      ? this.referentials
          .delegations()
          [card.governorateCode]?.find((candidate) => candidate.code === card.delegationCode)
      : undefined;
    return [governorate, delegation]
      .filter((element): element is Governorate | Delegation => !!element)
      .map((element) => labelOf(element.label, lang))
      .join(', ');
  }
}

/** Text of an attribute value; a false flag says nothing. */
export function valueText(
  definition: AttributeDefinition,
  value: AttributeValue,
  lang: Lang,
  locale: string,
): string | null {
  const label = labelOf(definition.label, lang);
  switch (definition.type) {
    case 'ENUM': {
      const option = definition.values?.find((candidate) => candidate.code === value);
      return option ? labelOf(option.label, lang) : null;
    }
    case 'BOOLEAN':
      return value === true ? label : null;
    case 'INTEGER': {
      if (typeof value !== 'number') {
        return null;
      }
      const unit = attributeUnit(label);
      const number = `${attributePrefix(definition.code) ?? ''}${formatInteger(value, definition, locale)}`;
      return unit ? `${number} ${unit}` : number;
    }
    case 'TEXT':
      return String(value);
  }
}
