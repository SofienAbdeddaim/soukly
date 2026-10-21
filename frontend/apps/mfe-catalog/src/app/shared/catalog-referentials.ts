import { computed, inject, Injectable, signal } from '@angular/core';
import {
  AttributeDefinition,
  CategoryFamily,
  Delegation,
  Governorate,
  ReferentialsApi,
  Subcategory,
} from '@soukly/listing-data';

/**
 * Categories and places of CAT-01 as signals, for the labels of the cards, the chips and the
 * filters. Each list is requested once (ReferentialsApi keeps the answers); a label whose list is
 * not there yet is simply missing, and appears once it arrives. A failed request can be tried again.
 */
@Injectable({ providedIn: 'root' })
export class CatalogReferentials {
  private readonly api = inject(ReferentialsApi);
  private readonly requested = new Set<string>();

  readonly families = signal<CategoryFamily[]>([]);
  readonly governorates = signal<Governorate[]>([]);
  /** Delegations by governorate code. */
  readonly delegations = signal<Record<string, Delegation[]>>({});
  /** Attributes by subcategory code. */
  readonly attributes = signal<Record<string, AttributeDefinition[]>>({});

  readonly subcategories = computed(() => {
    const map = new Map<string, Subcategory>();
    for (const family of this.families()) {
      for (const subcategory of family.subcategories) {
        map.set(subcategory.code, subcategory);
      }
    }
    return map;
  });

  /** Categories and governorates; resolves false when one of them could not be read. */
  async loadBasics(): Promise<boolean> {
    const [families, governorates] = await Promise.all([
      this.once(
        'families',
        () => this.api.categories(),
        (value) => this.families.set(value),
      ),
      this.once(
        'governorates',
        () => this.api.governorates(),
        (value) => this.governorates.set(value),
      ),
    ]);
    return families && governorates;
  }

  loadDelegations(governorate: string): Promise<boolean> {
    return this.once(
      `delegations:${governorate}`,
      () => this.api.delegations(governorate),
      (value) => this.delegations.update((all) => ({ ...all, [governorate]: value })),
    );
  }

  loadAttributes(subcategory: string): Promise<boolean> {
    return this.once(
      `attributes:${subcategory}`,
      () => this.api.attributes(subcategory),
      (value) => this.attributes.update((all) => ({ ...all, [subcategory]: value })),
    );
  }

  private async once<T>(key: string, load: () => Promise<T>, store: (value: T) => void) {
    if (this.requested.has(key)) {
      return true;
    }
    this.requested.add(key);
    try {
      store(await load());
      return true;
    } catch {
      this.requested.delete(key);
      return false;
    }
  }
}
