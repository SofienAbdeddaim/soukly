import { inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationSkipped,
  RouteConfigLoadStart,
  Router,
} from '@angular/router';
import { filter, map } from 'rxjs';

/** True while the router downloads the routes of a remote: the content area shows a skeleton. */
@Injectable({ providedIn: 'root' })
export class RemoteLoading {
  readonly active = toSignal(
    inject(Router).events.pipe(
      filter(
        (event) =>
          event instanceof RouteConfigLoadStart ||
          event instanceof NavigationEnd ||
          event instanceof NavigationCancel ||
          event instanceof NavigationError ||
          event instanceof NavigationSkipped,
      ),
      map((event) => event instanceof RouteConfigLoadStart),
    ),
    { initialValue: false },
  );
}
