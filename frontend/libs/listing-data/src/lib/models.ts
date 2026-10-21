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

export type ListingStatus = 'DRAFT' | 'PUBLISHED' | 'EXPIRED';
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
}

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
