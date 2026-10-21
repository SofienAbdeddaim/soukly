import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiPhotoTile, UiPhotoTileState } from './photo-tile';

@Component({
  imports: [UiPhotoTile],
  template: `
    <ui-photo-tile
      src="/photo.webp"
      alt="Photo 1 sur 3"
      [state]="state()"
      [cover]="cover()"
      coverLabel="Couverture"
      [progress]="60"
      progressLabel="Envoi de la photo"
      errorText="Échec de l'envoi"
      retryable
      retryLabel="Réessayer"
      removeLabel="Supprimer la photo"
      [canMoveBefore]="false"
      canMoveAfter
      moveBeforeLabel="Déplacer avant"
      moveAfterLabel="Déplacer après"
      (remove)="events.push('remove')"
      (retry)="events.push('retry')"
      (moveAfter)="events.push('after')"
    />
  `,
})
class Host {
  state = signal<UiPhotoTileState>('ready');
  cover = signal(true);
  events: string[] = [];
}

describe('UiPhotoTile', () => {
  async function setup(state: UiPhotoTileState = 'ready') {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.state.set(state);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const button = (name: string) =>
      [...element.querySelectorAll('button')].find(
        (item) => (item.getAttribute('aria-label') ?? item.textContent?.trim()) === name,
      ) as HTMLButtonElement | undefined;
    return { fixture, host: fixture.componentInstance, element, button };
  }

  it('shows a ready photo with its badge, delete and move buttons', async () => {
    const { element, host, button } = await setup();
    expect(element.querySelector('img')?.getAttribute('alt')).toBe('Photo 1 sur 3');
    expect(element.textContent).toContain('Couverture');
    expect(button('Déplacer avant')?.disabled).toBe(true);

    button('Déplacer après')?.click();
    button('Supprimer la photo')?.click();
    expect(host.events).toEqual(['after', 'remove']);
  });

  it('shows the progress of an upload, without delete button', async () => {
    const { element, button } = await setup('uploading');
    expect(element.querySelector('[role=progressbar]')?.getAttribute('aria-valuenow')).toBe('60');
    expect(button('Supprimer la photo')).toBeUndefined();
    expect(element.textContent).not.toContain('Couverture');
  });

  it('offers to retry a failed upload', async () => {
    const { element, host, button } = await setup('failed');
    expect(element.textContent).toContain("Échec de l'envoi");
    button('Réessayer')?.click();
    expect(host.events).toEqual(['retry']);
  });
});
