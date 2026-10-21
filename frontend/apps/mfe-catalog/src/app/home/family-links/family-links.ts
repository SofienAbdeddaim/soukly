import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  LucideBike,
  LucideBriefcase,
  LucideCar,
  LucideDynamicIcon,
  LucideHouse,
  LucideIcon,
  LucidePackage,
  LucidePawPrint,
  LucideShirt,
  LucideSmartphone,
  LucideSofa,
  LucideWrench,
} from '@lucide/angular';
import { LocaleService } from '@soukly/i18n';
import { labelOf } from '@soukly/listing-data';
import { CatalogReferentials } from '../../shared/catalog-referentials';

/** Icon of each family of CAT-01 (Lucide, SETUP-04); a new family gets the generic box. */
export const FAMILY_ICONS: Record<string, LucideIcon> = {
  vehicles: LucideCar,
  'real-estate': LucideHouse,
  jobs: LucideBriefcase,
  services: LucideWrench,
  home: LucideSofa,
  multimedia: LucideSmartphone,
  fashion: LucideShirt,
  leisure: LucideBike,
  animals: LucidePawPrint,
  misc: LucidePackage,
};

/**
 * The 10 families with their icon, each a link to its results (`/{lang}/c/{family}`), with the
 * remembered governorate when there is one.
 */
@Component({
  selector: 'app-family-links',
  imports: [RouterLink, LucideDynamicIcon],
  template: `
    <ul class="families" data-testid="families">
      @for (family of families(); track family.code) {
        <li>
          <a
            class="families__link"
            [routerLink]="['/', locale.lang(), 'c', family.code]"
            [queryParams]="governorate() ? { governorate: governorate() } : {}"
          >
            <span class="families__icon" aria-hidden="true">
              <svg [lucideIcon]="family.icon" [size]="24" aria-hidden="true"></svg>
            </span>
            <span class="families__label">{{ family.label }}</span>
          </a>
        </li>
      }
    </ul>
  `,
  styleUrl: './family-links.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyLinks {
  protected readonly locale = inject(LocaleService);
  private readonly referentials = inject(CatalogReferentials);

  readonly governorate = input<string | null>(null);

  protected readonly families = computed(() =>
    this.referentials.families().map((family) => ({
      code: family.code,
      label: labelOf(family.label, this.locale.lang()),
      icon: FAMILY_ICONS[family.code] ?? LucidePackage,
    })),
  );
}
