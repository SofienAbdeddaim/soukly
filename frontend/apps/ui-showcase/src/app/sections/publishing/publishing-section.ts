import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { UiPhotoTile, UiProgress, UiToggleGroup, UiToggleOption } from '@soukly/ui';
import { ShowcaseLocale } from '../../core/showcase-locale';
import { ShowcaseSection } from '../../layout/showcase-section/showcase-section';

/** Components of the publish form (ANN-04): ui-toggle-group, ui-progress, ui-photo-tile. */
@Component({
  selector: 'app-publishing-section',
  imports: [ShowcaseSection, UiPhotoTile, UiProgress, UiToggleGroup],
  template: `
    <app-showcase-section [heading]="t().publishing" panelClass="grid gap-6 md:max-w-xl">
      <ui-toggle-group
        layout="tiles"
        required
        [legend]="t().familiesLabel"
        [options]="families()"
        [value]="family()"
        (valueChange)="family.set($event)"
      />
      <ui-toggle-group
        [legend]="t().fuelLabel"
        [optionalLabel]="t().optional"
        [options]="fuels()"
        [value]="fuel()"
        (valueChange)="fuel.set($event)"
      />
      <div class="grid gap-2 rounded-md bg-brand p-4">
        <ui-progress
          tone="on-brand"
          [value]="40"
          [label]="t().progressLabel"
          [valueText]="t().progressText"
        />
      </div>
      <ui-progress [value]="65" [label]="t().uploading" />
      <ul class="m-0 grid list-none grid-cols-3 gap-2 p-0">
        <li>
          <ui-photo-tile
            [alt]="t().photoAlt"
            cover
            [coverLabel]="t().cover"
            [removeLabel]="t().removePhoto"
            canMoveAfter
            [moveBeforeLabel]="t().moveBefore"
            [moveAfterLabel]="t().moveAfter"
          />
        </li>
        <li>
          <ui-photo-tile
            [alt]="t().uploading"
            state="uploading"
            [progress]="60"
            [progressLabel]="t().uploading"
            [removeLabel]="t().removePhoto"
          />
        </li>
        <li>
          <ui-photo-tile
            [alt]="t().uploadFailed"
            state="failed"
            [errorText]="t().uploadFailed"
            retryable
            [retryLabel]="t().retry"
            [removeLabel]="t().removePhoto"
          />
        </li>
      </ul>
    </app-showcase-section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublishingSection {
  protected readonly t = inject(ShowcaseLocale).labels;
  protected readonly family = signal<string | null>('0');
  protected readonly fuel = signal<string | null>('1');
  protected readonly families = computed<UiToggleOption[]>(() =>
    this.t().families.map((label, index) => ({ value: String(index), label })),
  );
  protected readonly fuels = computed<UiToggleOption[]>(() =>
    this.t().fuels.map((label, index) => ({ value: String(index), label })),
  );
}
