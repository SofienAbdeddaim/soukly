import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AlternateLinks } from './alternate-links';

@Component({ template: '' })
class Page {}

describe('AlternateLinks', () => {
  afterEach(() =>
    document.head.querySelectorAll('link[rel="alternate"]').forEach((link) => link.remove()),
  );

  it('declares the three language variants of the page and x-default in French', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', component: Page }])],
    });
    TestBed.inject(AlternateLinks).start();

    await TestBed.inject(Router).navigateByUrl('/ar/publish/new?step=2');
    await TestBed.inject(Router).navigateByUrl('/en/account/login');

    const links = Array.from(
      document.head.querySelectorAll<HTMLLinkElement>('link[rel="alternate"]'),
    ).map((link) => [link.hreflang, link.href]);
    const origin = location.origin;
    expect(links).toEqual([
      ['fr', `${origin}/fr/account/login`],
      ['ar', `${origin}/ar/account/login`],
      ['en', `${origin}/en/account/login`],
      ['x-default', `${origin}/fr/account/login`],
    ]);
  });
});
