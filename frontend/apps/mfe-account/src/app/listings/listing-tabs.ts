import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  output,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { formatNumber, LocaleService } from '@soukly/i18n';
import { OwnListingStatus } from '@soukly/listing-data';
import { statusOf, Tab, TABS } from './own-listing-state';

/**
 * Tabs of "My listings" with their counts (WAI-ARIA tabs pattern): one tab stop, arrow keys move
 * between the tabs and select them, Home and End go to the first and the last one. A tab without
 * listing shows no count, as on the mockups.
 */
@Component({
  selector: 'app-listing-tabs',
  imports: [TranslocoPipe],
  template: `
    <div class="tabs" role="tablist" [attr.aria-label]="'account.listings.tabs.label' | transloco">
      @for (tab of tabs; track tab) {
        <button
          type="button"
          role="tab"
          class="tabs__tab"
          [id]="'listings-tab-' + tab"
          [attr.aria-selected]="tab === active()"
          [attr.aria-controls]="panelId()"
          [tabindex]="tab === active() ? 0 : -1"
          [attr.data-testid]="'tab-' + tab"
          (click)="selected.emit(tab)"
          (keydown)="keydown($event, tab)"
        >
          {{ 'account.listings.tabs.' + tab | transloco }}
          @if (countOf(tab); as count) {
            <span class="tabs__count">{{ count }}</span>
          }
        </button>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .tabs {
      display: flex;
      overflow-x: auto;
      border-block-end: 1px solid var(--color-border);
      scrollbar-width: none;
    }

    .tabs__tab {
      display: inline-flex;
      flex: none;
      align-items: center;
      gap: 4px;
      min-block-size: var(--control-height);
      padding-inline: 10px;
      border: 0;
      border-block-end: 3px solid transparent;
      background-color: transparent;
      color: var(--color-text-muted);
      font: inherit;
      font-size: 0.9375rem;
      font-weight: 500;
      white-space: nowrap;
      cursor: pointer;
    }

    .tabs__tab[aria-selected='true'] {
      border-block-end-color: var(--color-accent);
      color: var(--color-text);
      font-weight: 600;
    }

    .tabs__tab:focus-visible {
      outline: 2px solid transparent;
      box-shadow: var(--focus-ring);
    }

    .tabs__count {
      padding-inline: 6px;
      border-radius: var(--radius-full);
      background-color: var(--color-surface-muted);
      font-size: 0.75rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListingTabs {
  readonly active = input.required<Tab>();
  readonly counts = input.required<Partial<Record<OwnListingStatus, number>>>();
  /** Id of the tab panel the tabs control. */
  readonly panelId = input.required<string>();
  readonly selected = output<Tab>();

  private readonly locale = inject(LocaleService);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  protected readonly tabs = TABS;

  protected countOf(tab: Tab): string | null {
    const count = this.counts()[statusOf(tab)] ?? 0;
    return count > 0 ? formatNumber(count, this.locale.intlLocale()) : null;
  }

  protected keydown(event: KeyboardEvent, tab: Tab): void {
    const index = TABS.indexOf(tab);
    // In RTL the tabs run from right to left: the right arrow goes to the previous one.
    const forward = this.locale.dir() === 'rtl' ? 'ArrowLeft' : 'ArrowRight';
    const backward = this.locale.dir() === 'rtl' ? 'ArrowRight' : 'ArrowLeft';
    let next: number;
    switch (event.key) {
      case forward:
        next = (index + 1) % TABS.length;
        break;
      case backward:
        next = (index - 1 + TABS.length) % TABS.length;
        break;
      case 'Home':
        next = 0;
        break;
      case 'End':
        next = TABS.length - 1;
        break;
      default:
        return;
    }
    event.preventDefault();
    this.selected.emit(TABS[next]);
    this.element.querySelector<HTMLElement>(`#listings-tab-${TABS[next]}`)?.focus();
  }
}
