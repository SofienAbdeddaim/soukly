import { DOCUMENT, inject, Injectable } from '@angular/core';

/** Leaves the site for another one (the Facebook dialog); replaced by a fake in the tests. */
@Injectable({ providedIn: 'root' })
export class ExternalNavigation {
  private readonly document = inject(DOCUMENT);

  go(url: string): void {
    this.document.defaultView?.location.assign(url);
  }
}
