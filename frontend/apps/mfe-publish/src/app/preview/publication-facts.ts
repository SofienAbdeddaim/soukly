import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideBell, LucideCalendar } from '@lucide/angular';

/** How long the listing stays online and when its owner is reminded, before publishing. */
@Component({
  selector: 'app-publication-facts',
  imports: [TranslocoPipe, LucideBell, LucideCalendar],
  template: `
    <ul class="facts" data-testid="publication-facts">
      <li class="fact">
        <svg lucideCalendar [size]="18" aria-hidden="true"></svg>
        <span class="fact__text">
          <strong class="fact__title">{{ 'publish.preview.facts.online' | transloco }}</strong>
          <span class="fact__detail">{{ 'publish.preview.facts.online-detail' | transloco }}</span>
        </span>
      </li>
      <li class="fact">
        <svg lucideBell [size]="18" aria-hidden="true"></svg>
        <span class="fact__text">
          <strong class="fact__title">{{ 'publish.preview.facts.reminder' | transloco }}</strong>
          <span class="fact__detail">{{
            'publish.preview.facts.reminder-detail' | transloco
          }}</span>
        </span>
      </li>
    </ul>
  `,
  styleUrl: './publication-facts.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicationFacts {}
