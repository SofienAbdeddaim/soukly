import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucidePhone } from '@lucide/angular';
import { ListingStatus } from '@soukly/listing-data';
import { UiAlert, UiButton } from '@soukly/ui';
import { ContactState } from '../listing-page-store';

/**
 * "Show the number" of a listing page (CATA-03): a fixed bar at the bottom on mobile, a card in
 * the right column on desktop. A visitor is told that a login is needed; the owner sees "This is
 * your listing" with a link to their listings instead, never the button.
 */
@Component({
  selector: 'app-contact-panel',
  imports: [RouterLink, TranslocoPipe, LucidePhone, UiAlert, UiButton],
  template: `
    @if (owned()) {
      <div class="panel__owner" data-testid="owner-banner">
        <p class="panel__owner-title">{{ 'catalog.owner.title' | transloco }}</p>
        @if (status() === 'EXPIRED') {
          <p class="panel__owner-status">{{ 'catalog.owner.expired' | transloco }}</p>
        } @else if (status() === 'DRAFT') {
          <p class="panel__owner-status">{{ 'catalog.owner.draft' | transloco }}</p>
        }
        <a class="panel__owner-link" [routerLink]="myListingsCommands()">
          {{ 'catalog.owner.my-listings' | transloco }}
        </a>
      </div>
    } @else {
      @switch (contact().kind) {
        @case ('limit') {
          <ui-alert type="error" data-testid="contact-limit">
            {{ 'catalog.contact.limit' | transloco }}
          </ui-alert>
        }
        @case ('error') {
          <ui-alert type="error">{{ 'catalog.contact.error' | transloco }}</ui-alert>
        }
      }
      <button
        ui-button
        type="button"
        size="lg"
        class="panel__button"
        data-testid="show-number"
        [loading]="contact().kind === 'loading'"
        (click)="authenticated() ? reveal.emit() : login.emit()"
      >
        <svg lucidePhone [size]="18" aria-hidden="true"></svg>
        {{ 'catalog.contact.show' | transloco }}
      </button>
      @if (!authenticated()) {
        <p class="panel__hint" data-testid="login-hint">
          {{ 'catalog.contact.login-required' | transloco }}
        </p>
      }
    }
  `,
  styleUrl: './contact-panel.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContactPanel {
  readonly owned = input(false);
  readonly status = input<ListingStatus>('PUBLISHED');
  readonly authenticated = input(false);
  readonly contact = input.required<ContactState>();
  readonly myListingsCommands = input.required<string[]>();

  /** A signed-in member asks for the number. */
  readonly reveal = output<void>();
  /** A visitor asks for it: the login comes first. */
  readonly login = output<void>();
}
