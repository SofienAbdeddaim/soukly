import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideChevronDown, LucideCircleUser } from '@lucide/angular';
import { AuthService } from '@soukly/auth';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';

/**
 * Menu of the signed-in member in the header (disclosure pattern, like the language switcher): a
 * button with the display name, which opens "My account" and "Log out". Logging out closes the
 * session on the server, then goes back to the home page.
 */
@Component({
  selector: 'app-user-menu',
  imports: [RouterLink, TranslocoPipe, LocalePathPipe, LucideCircleUser, LucideChevronDown],
  host: {
    class: 'relative block',
    '(focusout)': 'onFocusOut($event)',
    '(keydown.escape)': 'closeAndFocus()',
    '(document:click)': 'onDocumentClick($event)',
  },
  template: `
    <button
      #toggle
      type="button"
      class="inline-flex min-h-11 max-w-48 items-center gap-2 rounded-md px-2 text-on-brand"
      aria-controls="user-menu"
      [attr.aria-expanded]="open()"
      data-testid="user-menu"
      (click)="open.set(!open())"
    >
      <svg lucideCircleUser [size]="22" aria-hidden="true"></svg>
      <span class="sr-only">{{ 'header.account-menu' | transloco }}:</span>
      <span class="truncate font-medium" data-testid="user-name">{{
        auth.user()?.displayName
      }}</span>
      <svg lucideChevronDown [size]="16" aria-hidden="true"></svg>
    </button>
    <ul
      id="user-menu"
      class="absolute end-0 top-full z-20 mt-2 min-w-48 list-none rounded-md bg-surface p-1 shadow-overlay"
      [hidden]="!open()"
    >
      <li>
        <a
          class="flex min-h-11 items-center rounded-sm px-3 text-text no-underline hover:bg-surface-muted"
          [routerLink]="'/account' | localePath"
          (click)="open.set(false)"
        >
          {{ 'header.my-account' | transloco }}
        </a>
      </li>
      <li>
        <a
          class="flex min-h-11 items-center rounded-sm px-3 text-text no-underline hover:bg-surface-muted"
          [routerLink]="'/account/listings' | localePath"
          data-testid="user-menu-listings"
          (click)="open.set(false)"
        >
          {{ 'header.my-listings' | transloco }}
        </a>
      </li>
      <li>
        <button
          type="button"
          class="flex min-h-11 w-full items-center rounded-sm px-3 text-start text-text hover:bg-surface-muted"
          [disabled]="loggingOut()"
          (click)="logout()"
        >
          {{ 'header.logout' | transloco }}
        </button>
      </li>
    </ul>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserMenu {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly toggleButton = viewChild.required<ElementRef<HTMLButtonElement>>('toggle');

  protected readonly open = signal(false);
  protected readonly loggingOut = signal(false);

  protected async logout(): Promise<void> {
    this.loggingOut.set(true);
    await this.auth.logout();
    this.loggingOut.set(false);
    this.open.set(false);
    await this.router.navigateByUrl(`/${this.locale.lang()}`);
  }

  protected closeAndFocus(): void {
    if (this.open()) {
      this.open.set(false);
      this.toggleButton().nativeElement.focus();
    }
  }

  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (next && !this.host.nativeElement.contains(next)) {
      this.open.set(false);
    }
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
