import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  Injector,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { FormField, form, required, submit, validate } from '@angular/forms/signals';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LucideClock, LucideEye, LucideEyeOff } from '@lucide/angular';
import { AuthService, problemCode, problemFieldErrors } from '@soukly/auth';
import { LocalePathPipe, LocaleService } from '@soukly/i18n';
import {
  ToastService,
  UiAlert,
  UiButton,
  UiField,
  UiFieldControl,
  UiFieldSuffix,
} from '@soukly/ui';
import { combineLatest, take } from 'rxjs';
import { codeError, codePointLength, errorKey } from '../shared/validation';

/**
 * "Choose a new password" (`/{lang}/account/reset-password?token=…`), opened from the reset email.
 * The token is read, then removed from the address bar and kept in memory; while the page is open
 * no `Referer` is sent. On success user-service signs out every other device and opens a session
 * here: the member goes home with a toast. A refused link shows "This link is no longer valid".
 */
@Component({
  selector: 'app-reset-password-page',
  imports: [
    FormField,
    RouterLink,
    TranslocoPipe,
    LocalePathPipe,
    LucideClock,
    LucideEye,
    LucideEyeOff,
    UiAlert,
    UiButton,
    UiField,
    UiFieldControl,
    UiFieldSuffix,
  ],
  templateUrl: './reset-password-page.html',
  styleUrl: '../account-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPasswordPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly meta = inject(Meta);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly generalAlert = viewChild(UiAlert);
  private token: string | null = null;

  protected readonly model = signal({ password: '' });
  protected readonly form = form(this.model, (path) => {
    required(path.password, { error: codeError('password.required') });
    validate(path.password, ({ value }) => {
      const length = codePointLength(value());
      return value() && (length < 8 || length > 128) ? codeError('password.length') : null;
    });
  });

  protected readonly state = signal<'form' | 'invalid'>('form');
  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly errorKey = errorKey;

  constructor() {
    // The token must never leave in a Referer, even to a resource loaded before it is removed.
    this.meta.updateTag({ name: 'referrer', content: 'no-referrer' });
    inject(DestroyRef).onDestroy(() => this.meta.removeTag('name="referrer"'));
  }

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (this.token) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { token: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    } else {
      this.state.set('invalid');
    }
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.generalError.set(null);
    this.form().markAsTouched();
    if (this.form().invalid()) {
      this.form.password().focusBoundControl();
      return;
    }
    this.submitting.set(true);
    let signedIn = false;
    await submit(this.form, {
      action: async () => {
        try {
          await this.auth.resetPassword(this.token ?? '', this.model().password);
          signedIn = true;
          return undefined;
        } catch (error) {
          return this.apiErrors(error);
        }
      },
    });
    this.submitting.set(false);
    if (signedIn) {
      await this.router.navigateByUrl(`/${this.locale.lang()}`);
      this.toastSignedIn();
      return;
    }
    afterNextRender(
      () => {
        if (this.form().invalid()) {
          this.form.password().focusBoundControl();
        } else if (this.generalError()) {
          this.generalAlert()?.focus();
        }
      },
      { injector: this.injector },
    );
  }

  /** Password codes under the field; a refused link changes the screen; anything else on top. */
  private apiErrors(error: unknown) {
    const code = problemCode(error);
    if (code === 'reset-token-invalid') {
      this.state.set('invalid');
      return undefined;
    }
    const fieldErrors =
      code === 'validation-failed'
        ? problemFieldErrors(error).filter((item) => item.field === 'newPassword')
        : [];
    if (fieldErrors.length > 0) {
      return fieldErrors.map((item) => ({ fieldTree: this.form.password, kind: item.code }));
    }
    this.generalError.set(
      error instanceof HttpErrorResponse && error.status === 429
        ? 'account.errors.too-many-attempts'
        : 'account.errors.unexpected',
    );
    return undefined;
  }

  private toastSignedIn(): void {
    combineLatest([
      this.transloco.selectTranslate<string>('reset.signed-in', {}, 'account'),
      this.transloco.selectTranslate<string>('reset.close', {}, 'account'),
    ])
      .pipe(take(1))
      .subscribe(([message, closeLabel]) =>
        this.toasts.show({ type: 'success', message, closeLabel }),
      );
  }
}
