import { computed, Directive, DOCUMENT, inject, input } from '@angular/core';
import { appLinkFor, detectLinkPlatform } from './app-link';

/**
 * An outbound link to Facebook or Instagram that opens the app on phones and tablets, where the
 * member is signed in, and a new tab on desktop. See {@link appLinkFor}.
 *
 * `<a [dataSocialAppLink]="post.permalink" #link="socialAppLink">…</a>`, then `link.opensNewTab()`.
 */
@Directive({
  selector: 'a[dataSocialAppLink]',
  exportAs: 'socialAppLink',
  host: {
    '[attr.href]': 'link().href',
    '[attr.target]': 'link().target',
    rel: 'noopener noreferrer',
  },
})
export class SocialAppLink {
  readonly dataSocialAppLink = input.required<string>();

  private readonly platform = detectLinkPlatform(inject(DOCUMENT).defaultView?.navigator);

  protected readonly link = computed(() => appLinkFor(this.dataSocialAppLink(), this.platform));

  /** Whether the link opens a new tab (desktop), for a label that says so. */
  readonly opensNewTab = computed(() => this.link().target === '_blank');
}
