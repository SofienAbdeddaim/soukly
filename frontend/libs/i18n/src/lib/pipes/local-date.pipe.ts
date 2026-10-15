import { inject, Pipe, PipeTransform } from '@angular/core';
import { DateInput, formatLocalDate } from '../format';
import { LocaleService } from '../locale.service';

/** `{{ listing.createdAt | localDate }}`: "12 oct. 2026", "12/10/2026", "Oct 12, 2026". */
@Pipe({ name: 'localDate', pure: false })
export class LocalDatePipe implements PipeTransform {
  private readonly locale = inject(LocaleService);

  transform(value: DateInput | null | undefined): string {
    return value == null ? '' : formatLocalDate(value, this.locale.intlLocale());
  }
}
