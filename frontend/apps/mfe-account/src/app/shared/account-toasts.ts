import { inject, Injectable } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { ToastService } from '@soukly/ui';
import { combineLatest, take } from 'rxjs';

/**
 * Success toasts of the account screens, with a text of the "account" scope (`profile.saved`).
 * Translated when the scope is loaded, so a toast shown right after a navigation still has its
 * text.
 */
@Injectable({ providedIn: 'root' })
export class AccountToasts {
  private readonly transloco = inject(TranslocoService);
  private readonly toasts = inject(ToastService);

  success(key: string): void {
    combineLatest([
      this.transloco.selectTranslate<string>(key, {}, 'account'),
      this.transloco.selectTranslate<string>('common.close', {}, 'account'),
    ])
      .pipe(take(1))
      .subscribe(([message, closeLabel]) =>
        this.toasts.show({ type: 'success', message, closeLabel }),
      );
  }
}
