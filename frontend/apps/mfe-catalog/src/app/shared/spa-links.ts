import { Directive, DOCUMENT, inject } from '@angular/core';
import { Router } from '@angular/router';

/**
 * Opens the links of the listing cards inside the app, without reloading the page: the cards of
 * libs/ui render plain `href` links, this directive hands a plain click on one of them to the
 * router. Ctrl, Cmd, Shift, a middle click or another origin keep the browser behaviour.
 */
@Directive({
  selector: '[appSpaLinks]',
  host: { '(click)': 'onClick($event)' },
})
export class SpaLinks {
  private readonly router = inject(Router);
  private readonly origin = inject(DOCUMENT).location.origin;

  protected onClick(event: MouseEvent): void {
    if (event.defaultPrevented || event.button !== 0) {
      return;
    }
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
      return;
    }
    const link = (event.target as Element | null)?.closest?.('a[href]');
    if (!(link instanceof HTMLAnchorElement) || (link.target && link.target !== '_self')) {
      return;
    }
    const url = new URL(link.href, this.origin);
    if (url.origin !== this.origin) {
      return;
    }
    event.preventDefault();
    void this.router.navigateByUrl(url.pathname + url.search + url.hash);
  }
}
