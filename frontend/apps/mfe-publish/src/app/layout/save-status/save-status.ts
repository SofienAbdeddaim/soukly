import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideCheck, LucideCloudOff } from '@lucide/angular';
import { DraftStore } from '../../draft/draft-store';

/**
 * Discreet state of the automatic saving ("Draft saved", "Saving…", "Offline, trying again…"),
 * announced politely. A conflict is shown above the form instead (StepLayout).
 */
@Component({
  selector: 'app-save-status',
  imports: [TranslocoPipe, LucideCheck, LucideCloudOff],
  template: `
    <p class="save-status" role="status" data-testid="save-status">
      @switch (state()) {
        @case ('saved') {
          <svg lucideCheck [size]="14" aria-hidden="true"></svg>
          {{ 'publish.save.saved' | transloco }}
        }
        @case ('saving') {
          {{ 'publish.save.saving' | transloco }}
        }
        @case ('offline') {
          <svg lucideCloudOff [size]="14" aria-hidden="true"></svg>
          {{ 'publish.save.offline' | transloco }}
        }
        @case ('invalid') {
          {{ 'publish.save.invalid' | transloco }}
        }
        @case ('error') {
          {{ 'publish.save.error' | transloco }}
        }
      }
    </p>
  `,
  styles: `
    .save-status {
      display: flex;
      align-items: center;
      gap: 4px;
      margin: 0;
      color: var(--color-text-muted);
      font-size: 0.75rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaveStatus {
  private readonly store = inject(DraftStore);
  protected readonly state = computed(() => this.store.saveState());
}
