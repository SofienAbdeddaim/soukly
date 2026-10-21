import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UiToggleGroup, UiToggleOption } from './toggle-group';

@Component({
  imports: [UiToggleGroup],
  template: `
    <ui-toggle-group
      legend="Boîte de vitesses"
      [options]="options"
      [value]="value()"
      [required]="required()"
      [error]="error()"
      optionalLabel="(facultatif)"
      (valueChange)="value.set($event)"
    />
  `,
})
class Host {
  options: UiToggleOption[] = [
    { value: 'manual', label: 'Manuelle' },
    { value: 'automatic', label: 'Automatique' },
  ];
  value = signal<string | null>(null);
  required = signal(false);
  error = signal<string | null>(null);
}

describe('UiToggleGroup', () => {
  async function setup() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const buttons = () => [...element.querySelectorAll('button')] as HTMLButtonElement[];
    return { fixture, host: fixture.componentInstance, element, buttons };
  }

  it('groups the options in a fieldset named by its legend', async () => {
    const { element, buttons } = await setup();
    expect(element.querySelector('fieldset legend')?.textContent).toContain('Boîte de vitesses');
    expect(element.querySelector('legend')?.textContent).toContain('(facultatif)');
    expect(buttons().map((button) => button.getAttribute('aria-pressed'))).toEqual([
      'false',
      'false',
    ]);
  });

  it('presses one option at a time', async () => {
    const { fixture, host, buttons } = await setup();
    buttons()[0].click();
    await fixture.whenStable();
    buttons()[1].click();
    await fixture.whenStable();

    expect(host.value()).toBe('automatic');
    expect(buttons().map((button) => button.getAttribute('aria-pressed'))).toEqual([
      'false',
      'true',
    ]);
  });

  it('unselects an optional choice on a second press, never a required one', async () => {
    const { fixture, host, buttons } = await setup();
    host.value.set('manual');
    await fixture.whenStable();
    buttons()[0].click();
    expect(host.value()).toBeNull();

    host.value.set('manual');
    host.required.set(true);
    await fixture.whenStable();
    buttons()[0].click();
    expect(host.value()).toBe('manual');
  });

  it('describes the group by its error', async () => {
    const { fixture, host, element } = await setup();
    host.error.set('Choisissez une valeur.');
    await fixture.whenStable();

    const fieldset = element.querySelector('fieldset') as HTMLFieldSetElement;
    const error = element.querySelector(`#${fieldset.getAttribute('aria-describedby')}`);
    expect(error?.textContent).toContain('Choisissez une valeur.');
  });

  it('presses several options of a multiple group, in the order of the options', async () => {
    @Component({
      imports: [UiToggleGroup],
      template: `
        <ui-toggle-group
          legend="Carburant"
          multiple
          [options]="options"
          [values]="values()"
          (valuesChange)="values.set($event)"
        />
      `,
    })
    class MultipleHost {
      options = [
        { value: 'petrol', label: 'Essence' },
        { value: 'diesel', label: 'Diesel' },
        { value: 'lpg', label: 'GPL' },
      ];
      values = signal<string[]>(['lpg']);
    }
    const fixture = TestBed.createComponent(MultipleHost);
    await fixture.whenStable();
    const buttons = () =>
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));

    buttons()[0].click();
    await fixture.whenStable();
    expect(fixture.componentInstance.values()).toEqual(['petrol', 'lpg']);
    expect(buttons().map((button) => button.getAttribute('aria-pressed'))).toEqual([
      'true',
      'false',
      'true',
    ]);
    buttons()[2].click();
    expect(fixture.componentInstance.values()).toEqual(['petrol']);
  });
});
