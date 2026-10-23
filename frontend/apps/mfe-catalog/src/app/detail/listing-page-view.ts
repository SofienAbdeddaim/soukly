import { inject, Injectable } from '@angular/core';
import { formatPrice, formatRelativeTime, Lang, LocaleService } from '@soukly/i18n';
import {
  AttributeDefinition,
  AttributeValue,
  labelOf,
  Listing,
  ListingSeller,
  priceUnit,
  PublicListing,
  withoutUnit,
} from '@soukly/listing-data';
import { CatalogReferentials } from '../shared/catalog-referentials';
import { valueText } from '../shared/listing-view';

/** A specific attribute of the listing as the page shows it: "Kilométrage" · "62 000 km". */
export interface ListingFact {
  code: string;
  label: string;
  /** The value, or null for a yes/no attribute, whose text is `catalog.detail.yes` or `.no`. */
  value: string | null;
  yes?: boolean;
}

export interface SellerView {
  displayName: string;
  /** First letter of the name, for the round badge. */
  initial: string;
  /** "mars 2026", in the language of the page. */
  memberSince: string;
}

/** A listing page ready for its template, every text formatted in the language of the page. */
export interface ListingPageView {
  id: string;
  title: string;
  /** Formatted price, null "on quote" (`catalog.card.on-quote`). */
  price: string | null;
  /** Key of the price unit: " / mois", " / nuit". */
  unitKey: string | null;
  negotiable: boolean;
  place: string;
  governorate: string;
  dateLabel: string | null;
  dateTime: string | null;
  description: string;
  facts: ListingFact[];
  familyCode: string;
  familyLabel: string;
  subcategoryLabel: string;
  seller: SellerView | null;
  /** Short reference of the Instagram captions (SOC-05), null when the API gave none. */
  shortRef: string | null;
}

/**
 * Builds the view of a listing page (CATA-03) from the listing and the referentials of CAT-01: the
 * filled specific attributes in the order and with the labels and units of the subcategory.
 */
@Injectable({ providedIn: 'root' })
export class ListingPageViews {
  private readonly locale = inject(LocaleService);
  private readonly referentials = inject(CatalogReferentials);

  /** Categories and governorates, for the labels of the "no longer available" page. */
  prepareBasics(): void {
    void this.referentials.loadBasics();
  }

  /** Asks for the labels the page needs: attributes of the subcategory, delegations. */
  prepare(listing: PublicListing | Listing): void {
    void this.referentials.loadBasics();
    void this.referentials.loadAttributes(listing.categoryCode);
    if (listing.governorateCode && listing.delegationCode) {
      void this.referentials.loadDelegations(listing.governorateCode);
    }
  }

  view(listing: PublicListing | Listing): ListingPageView {
    const lang = this.locale.lang();
    const locale = this.locale.intlLocale();
    const unit = priceUnit(listing.categoryCode, listing.attributes);
    const subcategory = this.referentials.subcategories().get(listing.categoryCode);
    const family = this.referentials
      .families()
      .find((candidate) => candidate.code === subcategory?.familyCode);
    const governorate = this.governorateLabel(listing.governorateCode, lang);
    return {
      id: listing.id,
      title: listing.title ?? '',
      price:
        listing.priceMode === 'ON_QUOTE' || listing.price === null
          ? null
          : formatPrice(listing.price, locale),
      unitKey: unit ? `catalog.card.per-${unit}` : null,
      negotiable: listing.negotiable,
      place: this.place(listing, lang),
      governorate,
      dateLabel: listing.publishedAt ? formatRelativeTime(listing.publishedAt, locale) : null,
      dateTime: listing.publishedAt,
      description: listing.description ?? '',
      facts: this.facts(listing, lang, locale),
      familyCode: subcategory?.familyCode ?? listing.categoryCode.split('.')[0],
      familyLabel: family ? labelOf(family.label, lang) : '',
      subcategoryLabel: subcategory ? labelOf(subcategory.label, lang) : '',
      seller: listing.seller ? sellerView(listing.seller, locale) : null,
      shortRef: listing.shortRef ?? null,
    };
  }

  /** Label of a subcategory and of a governorate, for the "no longer available" page. */
  subcategoryLabel(code: string | null): string {
    const subcategory = code ? this.referentials.subcategories().get(code) : undefined;
    return subcategory ? labelOf(subcategory.label, this.locale.lang()) : '';
  }

  governorateLabel(code: string | null, lang: Lang = this.locale.lang()): string {
    const governorate = this.referentials
      .governorates()
      .find((candidate) => candidate.code === code);
    return governorate ? labelOf(governorate.label, lang) : '';
  }

  private facts(listing: PublicListing | Listing, lang: Lang, locale: string): ListingFact[] {
    const definitions: AttributeDefinition[] =
      this.referentials.attributes()[listing.categoryCode] ?? [];
    const facts: ListingFact[] = [];
    for (const definition of definitions) {
      const value: AttributeValue | undefined = listing.attributes[definition.code];
      if (value === undefined || value === null) {
        continue;
      }
      const label = withoutUnit(labelOf(definition.label, lang));
      if (definition.type === 'BOOLEAN') {
        facts.push({ code: definition.code, label, value: null, yes: value === true });
        continue;
      }
      const text = valueText(definition, value, lang, locale);
      if (text) {
        facts.push({ code: definition.code, label, value: text });
      }
    }
    return facts;
  }

  /** "Sfax, Sakiet Ezzit", or the governorate alone. */
  private place(listing: PublicListing | Listing, lang: Lang): string {
    const governorate = this.governorateLabel(listing.governorateCode, lang);
    const delegation =
      listing.governorateCode && listing.delegationCode
        ? this.referentials
            .delegations()
            [listing.governorateCode]?.find(
              (candidate) => candidate.code === listing.delegationCode,
            )
        : undefined;
    return [governorate, delegation ? labelOf(delegation.label, lang) : '']
      .filter((part) => part)
      .join(', ');
  }
}

/** "Membre depuis mars 2026": the month of the registration, in the language of the page. */
export function sellerView(seller: ListingSeller, locale: string): SellerView {
  const [year, month] = seller.memberSince.split('-').map(Number);
  const since =
    year && month
      ? new Intl.DateTimeFormat(`${locale}-u-nu-latn`, {
          month: 'long',
          year: 'numeric',
          timeZone: 'UTC',
        }).format(new Date(Date.UTC(year, month - 1, 1)))
      : seller.memberSince;
  return {
    displayName: seller.displayName,
    initial: Array.from(seller.displayName.trim())[0]?.toLocaleUpperCase(locale) ?? '',
    memberSince: since,
  };
}
