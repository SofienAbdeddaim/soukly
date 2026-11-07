/** The three labels of a referential element (CAT-01); `labelOf` picks the one of the page. */
export interface Labels {
  fr: string;
  ar: string;
  en: string;
}

/** A subcategory, the only level a listing belongs to; `code` is `<family>.<subcategory>`. */
export interface Subcategory {
  code: string;
  familyCode: string;
  label: Labels;
}

/** First level of the category tree, with its active subcategories in display order. */
export interface CategoryFamily {
  code: string;
  label: Labels;
  subcategories: Subcategory[];
}

export type AttributeType = 'TEXT' | 'INTEGER' | 'ENUM' | 'BOOLEAN';

export interface EnumValue {
  code: string;
  label: Labels;
}

/**
 * Specific attribute of a subcategory, as published by `GET /categories/{code}/attributes`.
 * Bounds are inclusive and only present for the types they apply to; `maxValue` of a year is
 * already resolved by the API (current year + 1).
 */
export interface AttributeDefinition {
  code: string;
  type: AttributeType;
  required: boolean;
  minValue?: number;
  maxValue?: number;
  minLength?: number;
  maxLength?: number;
  filterable: boolean;
  label: Labels;
  /** ENUM only, in display order. */
  values?: EnumValue[];
}

export interface Governorate {
  code: string;
  label: Labels;
}

/** A delegation of a governorate; `code` is `<governorate>.<delegation>`. */
export interface Delegation {
  code: string;
  label: Labels;
}

/**
 * Life cycle of a listing (ANN-03, ANN-05): `INACTIVE` when its seller deactivated it, `DELETED`
 * never reaches the front end (answered `404` or `410`).
 */
export type ListingStatus = 'DRAFT' | 'PUBLISHED' | 'EXPIRED' | 'INACTIVE' | 'DELETED';
export type ListingKind = 'SALE' | 'SERVICE';
export type PriceMode = 'FIXED' | 'ON_QUOTE';
export type AttributeValue = string | number | boolean;

/** A READY photo of a listing (ANN-02); `position` 0 is the cover. */
export interface ListingPhoto {
  id: string;
  url: string;
  thumbnailUrl: string;
  width: number;
  height: number;
  position: number;
}

/**
 * A listing as answered to its owner (ANN-01 to ANN-03). Empty fields are `null`.
 * `missingFields` lists, in form order, what still prevents the publication: `categoryCode`,
 * `photos`, `title`, `description`, `priceMode`, `price`, `governorateCode`, `attributes.<code>`.
 */
export interface Listing {
  id: string;
  status: ListingStatus;
  version: number;
  kind: ListingKind;
  categoryCode: string;
  title: string | null;
  description: string | null;
  priceMode: PriceMode | null;
  /** Whole dinars (TND). */
  price: number | null;
  negotiable: boolean;
  governorateCode: string | null;
  delegationCode: string | null;
  attributes: Record<string, AttributeValue>;
  photos: ListingPhoto[];
  missingFields: string[];
  /** Automatic changes made by the last request: `attributes-removed`, `price-mode-reset`. */
  warnings: string[];
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  expiresAt: string | null;
  /** Public profile of the seller once the listing is not a draft (CATA-03), null for a draft. */
  seller?: ListingSeller | null;
  /**
   * Networks to share to when the draft is published (SOC-03), in the order FACEBOOK, INSTAGRAM;
   * null until the seller chose (the preview then ticks their preferences).
   */
  shareTo?: ShareNetwork[] | null;
  /** Language of the post texts, `fr`, `ar` or `en`, or null. */
  shareLanguage?: string | null;
  /**
   * Short reference of the listing (SOC-05), 8 characters such as `8F3C2K9A`: written in the
   * Instagram captions as `{host}/r/{shortRef}`. Missing from the answers of older versions.
   */
  shortRef?: string | null;
}

/** Networks a draft can be shared to (SOC-03). */
export type ShareNetwork = 'FACEBOOK' | 'INSTAGRAM';

/** The seller as a listing page shows them (CATA-03): never their phone number or email. */
export interface ListingSeller {
  id: string;
  displayName: string;
  /** Month of the registration, `2026-03`. */
  memberSince: string;
}

/**
 * A listing as `GET /listings/{id}` answers anyone but its owner (CATA-03): a published listing,
 * with its seller. The owner gets the full {@link Listing} instead, `seller` included.
 */
export interface PublicListing {
  id: string;
  status: ListingStatus;
  kind: ListingKind;
  categoryCode: string;
  title: string;
  description: string;
  priceMode: PriceMode;
  /** Whole dinars (TND); null "on quote". */
  price: number | null;
  currency: string;
  negotiable: boolean;
  governorateCode: string;
  delegationCode: string | null;
  attributes: Record<string, AttributeValue>;
  photos: ListingPhoto[];
  publishedAt: string;
  seller: ListingSeller;
  /** Short reference (SOC-05), shown as "Réf. 8F3C2K9A". */
  shortRef?: string | null;
}

/** A JSON Merge Patch (RFC 7396) of a draft: `null` clears a field, an absent one is unchanged. */
export interface ListingPatch {
  categoryCode?: string;
  title?: string | null;
  description?: string | null;
  priceMode?: PriceMode | null;
  price?: number | null;
  negotiable?: boolean | null;
  governorateCode?: string | null;
  delegationCode?: string | null;
  attributes?: Record<string, AttributeValue | null> | null;
  /** Draft only (SOC-03): `409 share-locked` once published. */
  shareTo?: ShareNetwork[] | null;
  shareLanguage?: string | null;
}

/** Statuses listed on "My listings" (ANN-05), one tab each, in the order of the tabs. */
export const OWN_LISTING_STATUSES = ['PUBLISHED', 'DRAFT', 'EXPIRED', 'INACTIVE'] as const;
export type OwnListingStatus = (typeof OWN_LISTING_STATUSES)[number];

/** A listing on "My listings" of its owner (`GET /listings/mine`, ANN-05). */
export interface OwnListingCard {
  id: string;
  status: OwnListingStatus;
  /** Null for a draft whose title is not filled in yet. */
  title: string | null;
  kind: ListingKind;
  categoryCode: string;
  priceMode: PriceMode | null;
  /** Whole dinars; null on quote or not filled in yet. */
  price: number | null;
  currency: string;
  /** 400 px WebP of the cover, null without photo. */
  coverThumbnailUrl: string | null;
  updatedAt: string;
  publishedAt: string | null;
  /** End of visibility; the past one of an expired listing; null for a draft. */
  expiresAt: string | null;
  /** Drafts only: required fields still empty, in form order. */
  missingFields?: string[];
  missingFieldsCount?: number;
  /** Members who were shown the phone number on this listing (CATA-03). */
  contactRevealsCount: number;
}

/** Body of `GET /listings/mine`: `counts` on the first page only, `nextCursor` absent on the last. */
export interface OwnListingsPage {
  items: OwnListingCard[];
  nextCursor?: string;
  counts?: Record<OwnListingStatus, number>;
}

/** A listing with the `ETag` of its version, to send back in `If-Match`. */
export interface VersionedListing {
  listing: Listing;
  etag: string;
}

/** Answer of an upload request: send the file with `method` to `uploadUrl`, with exactly `headers`. */
export interface UploadTicket {
  photoId: string;
  uploadUrl: string;
  method: string;
  headers: Record<string, string>;
  expiresAt: string;
}

/** Media types the photo API accepts (ANN-02). */
export const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;

/** Most photos a listing can have (ANN-02). */
export const MAX_PHOTOS = 10;

/** Largest original the photo API accepts (ANN-02: 5 MB). */
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
