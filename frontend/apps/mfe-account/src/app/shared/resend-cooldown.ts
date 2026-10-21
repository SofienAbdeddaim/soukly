import { DestroyRef, inject, signal } from '@angular/core';

/** Delay between two "Resend the email" clicks (AUTH-01 sends at most 3 emails an hour). */
export const RESEND_COOLDOWN_SECONDS = 60;

/**
 * Countdown that disables a resend button for 60 s after each click. Create it in an injection
 * context: the timer stops with the component.
 */
export class ResendCooldown {
  private readonly left = signal(0);
  private timer: ReturnType<typeof setInterval> | undefined;

  /** Seconds before the next click is allowed; 0 when allowed. */
  readonly secondsLeft = this.left.asReadonly();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  start(seconds = RESEND_COOLDOWN_SECONDS): void {
    this.stop();
    this.left.set(seconds);
    this.timer = setInterval(() => {
      this.left.update((value) => Math.max(value - 1, 0));
      if (this.left() === 0) {
        this.stop();
      }
    }, 1000);
  }

  private stop(): void {
    clearInterval(this.timer);
    this.timer = undefined;
  }
}
