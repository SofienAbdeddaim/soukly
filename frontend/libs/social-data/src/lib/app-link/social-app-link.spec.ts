import { Component, DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SocialAppLink } from './social-app-link';

@Component({
  imports: [SocialAppLink],
  template: `<a [dataSocialAppLink]="url">Post</a>`,
})
class Host {
  url = 'https://www.instagram.com/p/DAbCdEf123/';
}

function render(userAgent: string): HTMLAnchorElement {
  const fakeDocument = { defaultView: { navigator: { userAgent, maxTouchPoints: 0 } } };
  TestBed.configureTestingModule({ imports: [Host] });
  TestBed.overrideDirective(SocialAppLink, {
    add: { providers: [{ provide: DOCUMENT, useValue: fakeDocument }] },
  });
  const fixture = TestBed.createComponent(Host);
  fixture.detectChanges();
  return fixture.nativeElement.querySelector('a');
}

describe('SocialAppLink', () => {
  it('opens a new tab on desktop', () => {
    const link = render('Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0 Safari/537.36');
    expect(link.getAttribute('href')).toBe('https://www.instagram.com/p/DAbCdEf123/');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toBe('noopener noreferrer');
  });

  it('opens the app on Android', () => {
    const link = render(
      'Mozilla/5.0 (Linux; Android 14; SM-A546B) Chrome/131.0.0.0 Mobile Safari/537.36',
    );
    expect(link.getAttribute('href')).toMatch(/^intent:\/\/www\.instagram\.com\/p\/DAbCdEf123\//);
    expect(link.hasAttribute('target')).toBe(false);
  });
});
