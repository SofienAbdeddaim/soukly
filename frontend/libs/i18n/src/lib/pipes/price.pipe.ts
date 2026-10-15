import { inject, Pipe, PipeTransform } from '@angular/core';
import { formatPrice } from '../format';
import { LocaleService } from '../locale.service';

/**
 * `{{ 1250 | price }}`: whole dinars to a localized text ("1 250 DT"). Display it isolated from
 * the surrounding text (`<bdi>`), so Arabic text and digits do not mix.
 */
@Pipe({ name: 'price', pure: false })
export class PricePipe implements PipeTransform {
  private readonly locale = inject(LocaleService);

  transform(dinars: number | null | undefined): string {
    return dinars == null ? '' : formatPrice(dinars, this.locale.intlLocale());
  }
}
