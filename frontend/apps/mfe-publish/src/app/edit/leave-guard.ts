import { CanDeactivateFn } from '@angular/router';

/** A page that may hold changes not saved, and asks before they are lost. */
export interface ConfirmLeave {
  /** True when the page may be left: nothing to lose, or the member confirmed. */
  canLeave(): boolean | Promise<boolean>;
}

/** Asks the page before the router leaves it (links, browser back, "Cancel"). */
export const confirmLeaveGuard: CanDeactivateFn<ConfirmLeave> = (page) => page.canLeave();
