import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App (shell)', () => {
  it('renders the layout landmarks and the bottom bar', async () => {
    TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('header')).not.toBeNull();
    expect(element.querySelector('main#main-content')).not.toBeNull();
    expect(element.querySelector('footer')).not.toBeNull();
    expect(element.querySelector('nav[aria-label="Navigation principale"]')).not.toBeNull();
  });
});
