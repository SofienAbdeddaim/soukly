import {
  ChangeDetectionStrategy,
  Component,
  Injectable,
  Injector,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { LucideCircleAlert, LucideCircleCheck, LucideInfo, LucideX } from '@lucide/angular';
import { OverlayRef, createGlobalPositionStrategy, createOverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';

export type UiToastType = 'info' | 'success' | 'error';

export interface UiToastOptions {
  type?: UiToastType;
  message: string;
  /** Accessible name of the close button, for example "Close". */
  closeLabel: string;
  /** Milliseconds before the toast closes on its own; 0 keeps it open. Default: 5000. */
  duration?: number;
}

export interface UiToast {
  id: number;
  type: UiToastType;
  message: string;
  closeLabel: string;
}

export const UI_TOAST_DEFAULT_DURATION = 5000;
/** Delay before the first toast, so the live regions exist before their content changes. */
export const UI_TOAST_REGION_DELAY = 100;

/**
 * Shows short messages in a CDK overlay. Toasts of type `error` go to an `aria-live="assertive"`
 * region, the others to an `aria-live="polite"` region. A toast closes after `duration` (paused
 * while it is hovered or focused) or with its close button.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly injector = inject(Injector);
  private readonly items = signal<UiToast[]>([]);
  private readonly timers = new Map<
    number,
    { remaining: number; start: number; handle?: ReturnType<typeof setTimeout> }
  >();
  private overlayRef?: OverlayRef;
  private nextId = 0;

  readonly toasts = this.items.asReadonly();

  show(options: UiToastOptions): number {
    const toast: UiToast = {
      id: ++this.nextId,
      type: options.type ?? 'info',
      message: options.message,
      closeLabel: options.closeLabel,
    };
    const duration = options.duration ?? UI_TOAST_DEFAULT_DURATION;
    const add = () => {
      this.items.update((items) => [...items, toast]);
      if (duration > 0) {
        this.timers.set(toast.id, { remaining: duration, start: 0 });
        this.resume(toast.id);
      }
    };
    if (this.overlayRef) {
      add();
    } else {
      this.attachRegion();
      setTimeout(add, UI_TOAST_REGION_DELAY);
    }
    return toast.id;
  }

  dismiss(id: number): void {
    this.pause(id);
    this.timers.delete(id);
    this.items.update((items) => items.filter((toast) => toast.id !== id));
  }

  /** Stops the auto-close timer, for example while the toast is hovered or focused. */
  pause(id: number): void {
    const timer = this.timers.get(id);
    if (timer?.handle !== undefined) {
      clearTimeout(timer.handle);
      timer.handle = undefined;
      timer.remaining -= Date.now() - timer.start;
    }
  }

  resume(id: number): void {
    const timer = this.timers.get(id);
    if (timer && timer.handle === undefined) {
      timer.start = Date.now();
      timer.handle = setTimeout(() => this.dismiss(id), Math.max(timer.remaining, 0));
    }
  }

  private attachRegion(): void {
    this.overlayRef = createOverlayRef(this.injector, {
      positionStrategy: createGlobalPositionStrategy(this.injector)
        .bottom('24px')
        .centerHorizontally(),
      panelClass: 'ui-toast-overlay',
    });
    this.overlayRef.attach(new ComponentPortal(UiToastRegion, null, this.injector));
  }
}

/** One toast: icon, message and close button. Used by the toast region. */
@Component({
  selector: 'ui-toast',
  imports: [LucideCircleAlert, LucideCircleCheck, LucideInfo, LucideX],
  templateUrl: './toast.html',
  styleUrl: './toast.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': "'ui-toast ui-toast--' + toast().type",
    '(mouseenter)': 'service.pause(toast().id)',
    '(mouseleave)': 'service.resume(toast().id)',
    '(focusin)': 'service.pause(toast().id)',
    '(focusout)': 'service.resume(toast().id)',
  },
})
export class UiToastItem {
  protected readonly service = inject(ToastService);
  readonly toast = input.required<UiToast>();
}

/** The two live regions of the toast overlay; created by `ToastService`. */
@Component({
  selector: 'ui-toast-region',
  imports: [UiToastItem],
  template: `
    <div class="ui-toast-region__list" aria-live="polite" aria-relevant="additions">
      @for (toast of polite(); track toast.id) {
        <ui-toast [toast]="toast" />
      }
    </div>
    <div class="ui-toast-region__list" aria-live="assertive" aria-relevant="additions">
      @for (toast of assertive(); track toast.id) {
        <ui-toast [toast]="toast" />
      }
    </div>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 8px;
      inline-size: min(440px, calc(100vi - 32px));
    }

    .ui-toast-region__list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UiToastRegion {
  private readonly service = inject(ToastService);
  protected readonly polite = computed(() =>
    this.service.toasts().filter((toast) => toast.type !== 'error'),
  );
  protected readonly assertive = computed(() =>
    this.service.toasts().filter((toast) => toast.type === 'error'),
  );
}
