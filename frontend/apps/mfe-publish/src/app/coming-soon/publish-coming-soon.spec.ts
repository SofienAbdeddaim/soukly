import { TestBed } from '@angular/core/testing';
import { PublishComingSoon } from './publish-coming-soon';

describe('PublishComingSoon', () => {
  it('shows the waiting message of the publishing area', async () => {
    TestBed.configureTestingModule({ imports: [PublishComingSoon] });
    const fixture = TestBed.createComponent(PublishComingSoon);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe(
      "Dépôt d'annonces Soukly, bientôt disponible",
    );
  });
});
