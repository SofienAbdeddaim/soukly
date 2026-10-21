import { Directive, ElementRef, inject, OnDestroy, OnInit, output } from '@angular/core';

/** Distance before the end of the list from which the next page is asked (CATA-02). */
export const NEAR_END_MARGIN = '0px 0px 600px 0px';

/**
 * Emits `nearEnd` when its element comes close to the viewport (`IntersectionObserver`), to load
 * the next page before the visitor reaches the bottom. Without `IntersectionObserver` nothing is
 * emitted: the "Show more" button stays the way to the next page.
 */
@Directive({ selector: '[appNearEnd]' })
export class NearEnd implements OnInit, OnDestroy {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private observer?: IntersectionObserver;

  readonly nearEnd = output<void>();

  ngOnInit(): void {
    if (typeof IntersectionObserver === 'undefined') {
      return;
    }
    this.observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          this.nearEnd.emit();
        }
      },
      { rootMargin: NEAR_END_MARGIN },
    );
    this.observer.observe(this.element);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }
}
