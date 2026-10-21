import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TestBed } from '@angular/core/testing';
import { LocaleService } from '@soukly/i18n';
import { Subject } from 'rxjs';
import { EMPTY_CRITERIA, SearchCriteria } from '../search/search-criteria';
import { catalogI18n, catalogProviders, FakeSearchApi } from '../testing/catalog-testing';
import { COUNT_DEBOUNCE_MS, FiltersDialog } from './filters-dialog';

describe('FiltersDialog', () => {
  afterEach(() => vi.useRealTimers());

  it('asks the number of listings 400 ms after the last change and applies only on click', async () => {
    vi.useFakeTimers();
    const counts: { criteria: SearchCriteria; answer: Subject<number | null> }[] = [];
    const close = vi.fn();
    TestBed.configureTestingModule({
      imports: [FiltersDialog, catalogI18n()],
      providers: [
        ...catalogProviders(new FakeSearchApi()),
        { provide: DialogRef, useValue: { close } },
        {
          provide: DIALOG_DATA,
          useValue: {
            criteria: { ...EMPTY_CRITERIA, q: 'peugeot' },
            total: 48,
            count: (criteria: SearchCriteria) => {
              const answer = new Subject<number | null>();
              counts.push({ criteria, answer });
              return answer;
            },
          },
        },
      ],
    });
    TestBed.inject(LocaleService).use('fr');
    const fixture = TestBed.createComponent(FiltersDialog);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const apply = () => element.querySelector('[data-testid="apply-filters"]') as HTMLButtonElement;
    expect(apply().textContent?.trim()).toBe('Voir 48 annonces');

    const governorate = element.querySelector(
      '[data-testid="filter-governorate"]',
    ) as HTMLSelectElement;
    governorate.value = 'sfax';
    governorate.dispatchEvent(new Event('change'));
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(COUNT_DEBOUNCE_MS - 1);
    expect(counts).toHaveLength(0);
    await vi.advanceTimersByTimeAsync(1);
    expect(counts).toHaveLength(1);
    expect(counts[0].criteria.governorate).toBe('sfax');
    counts[0].answer.next(31);
    fixture.detectChanges();
    expect(apply().textContent?.trim()).toBe('Voir 31 annonces');
    expect(close).not.toHaveBeenCalled();

    apply().click();
    expect(close).toHaveBeenCalledWith({ ...EMPTY_CRITERIA, q: 'peugeot', governorate: 'sfax' });
  });

  it('clears the filters but keeps the text query', async () => {
    const close = vi.fn();
    TestBed.configureTestingModule({
      imports: [FiltersDialog, catalogI18n()],
      providers: [
        ...catalogProviders(new FakeSearchApi()),
        { provide: DialogRef, useValue: { close } },
        {
          provide: DIALOG_DATA,
          useValue: {
            criteria: { ...EMPTY_CRITERIA, q: 'vélo', governorate: 'sfax', priceMax: 500 },
            total: 3,
            count: () => new Subject(),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(FiltersDialog);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const clear = Array.from(element.querySelectorAll('button')).find(
      (button) => button.textContent?.trim() === 'Tout effacer',
    ) as HTMLButtonElement;
    clear.click();
    (element.querySelector('[data-testid="apply-filters"]') as HTMLButtonElement).click();

    expect(close).toHaveBeenCalledWith({ ...EMPTY_CRITERIA, q: 'vélo' });
  });
});
