import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { LocaleService } from '@soukly/i18n';
import { TranslocoPipe } from '@jsverse/transloco';

/** Initial, display name and "Member since March 2026" at the top of the profile. */
@Component({
  selector: 'app-profile-identity',
  imports: [TranslocoPipe],
  template: `
    <span class="identity__initial" aria-hidden="true">{{ initial() }}</span>
    <div class="identity__text">
      <p class="identity__name" data-testid="profile-name">{{ displayName() }}</p>
      <p class="identity__since">
        {{ 'account.profile.member-since' | transloco: { date: since() } }}
      </p>
    </div>
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .identity__initial {
      display: grid;
      flex: none;
      place-items: center;
      inline-size: 52px;
      block-size: 52px;
      border-radius: var(--radius-full);
      background-color: var(--color-brand);
      color: var(--color-accent);
      font-size: 1.25rem;
      font-weight: 600;
    }

    .identity__text {
      min-inline-size: 0;
    }

    .identity__name {
      font-size: 1.0625rem;
      font-weight: 600;
      overflow-wrap: anywhere;
    }

    .identity__since {
      color: var(--color-text-muted);
      font-size: 0.8125rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileIdentity {
  private readonly locale = inject(LocaleService);

  readonly displayName = input.required<string>();
  /** ISO-8601 creation date of the account. */
  readonly createdAt = input.required<string>();

  protected readonly initial = computed(() => [...this.displayName().trim()][0]?.toUpperCase());
  /** "mars 2026", "مارس 2026", "March 2026", with Latin digits. */
  protected readonly since = computed(() =>
    new Intl.DateTimeFormat(`${this.locale.intlLocale()}-u-nu-latn`, {
      month: 'long',
      year: 'numeric',
    }).format(new Date(this.createdAt())),
  );
}
