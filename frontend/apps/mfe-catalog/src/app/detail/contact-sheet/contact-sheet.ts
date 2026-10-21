import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, DOCUMENT, inject } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LucideMessageCircle, LucidePhone, LucideShieldCheck, LucideX } from '@lucide/angular';
import { ToastService } from '@soukly/ui';
import { combineLatest, take } from 'rxjs';
import { callLink, formatPhone, whatsappLink } from '../contact-links';

export interface ContactSheetData {
  /** E.164, `+21620123456`. */
  phone: string;
  sellerName: string;
  sellerInitial: string;
  title: string;
  /** Address of the listing page, in the WhatsApp message. */
  url: string;
}

/**
 * "Contact {name}" (CATA-03): the number of the seller, readable left to right in every language,
 * with Call, WhatsApp (message filled in, in the language of the page) and Copy, and the safety
 * advice always visible. A CDK dialog shown as a bottom sheet on mobile, centered on desktop.
 */
@Component({
  selector: 'app-contact-sheet',
  imports: [TranslocoPipe, LucideMessageCircle, LucidePhone, LucideShieldCheck, LucideX],
  templateUrl: './contact-sheet.html',
  styleUrl: './contact-sheet.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contact-sheet', 'data-testid': 'contact-sheet' },
})
export class ContactSheet {
  protected readonly data = inject<ContactSheetData>(DIALOG_DATA);
  protected readonly dialogRef = inject(DialogRef);
  private readonly transloco = inject(TranslocoService);
  private readonly toasts = inject(ToastService);
  private readonly clipboard = inject(DOCUMENT).defaultView?.navigator.clipboard;

  protected readonly display = formatPhone(this.data.phone);
  protected readonly callHref = callLink(this.data.phone);

  protected whatsappHref(message: string): string {
    return whatsappLink(this.data.phone, message);
  }

  protected async copy(): Promise<void> {
    let copied = false;
    try {
      await this.clipboard?.writeText(this.data.phone);
      copied = !!this.clipboard;
    } catch {
      copied = false;
    }
    combineLatest([
      this.transloco.selectTranslate<string>(
        copied ? 'contact.copied' : 'contact.copy-failed',
        {},
        'catalog',
      ),
      this.transloco.selectTranslate<string>('actions.close', {}, 'catalog'),
    ])
      .pipe(take(1))
      .subscribe(([message, closeLabel]) =>
        this.toasts.show({ type: copied ? 'success' : 'error', message, closeLabel }),
      );
  }
}
