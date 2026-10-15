import { inject, Pipe, PipeTransform } from '@angular/core';
import { DateInput, formatRelativeTime } from '../format';
import { LocaleService } from '../locale.service';

/** `{{ listing.createdAt | relativeTime }}`: "hier", "il y a 2 heures", "أمس", "2 hours ago". */
@Pipe({ name: 'relativeTime', pure: false })
export class RelativeTimePipe implements PipeTransform {
  private readonly locale = inject(LocaleService);

  transform(value: DateInput | null | undefined): string {
    return value == null ? '' : formatRelativeTime(value, this.locale.intlLocale());
  }
}
