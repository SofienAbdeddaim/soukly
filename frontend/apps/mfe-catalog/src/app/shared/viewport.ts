import { BreakpointObserver } from '@angular/cdk/layout';
import { inject, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

/** Width from which the catalogue shows a grid and a filter column (SETUP-05: 768 px). */
export const DESKTOP_QUERY = '(min-width: 768px)';

/** True on desktop widths; follows the window when it is resized. In an injection context. */
export function injectIsDesktop(): Signal<boolean> {
  const breakpoints = inject(BreakpointObserver);
  return toSignal(breakpoints.observe(DESKTOP_QUERY).pipe(map((state) => state.matches)), {
    initialValue: breakpoints.isMatched(DESKTOP_QUERY),
  });
}
