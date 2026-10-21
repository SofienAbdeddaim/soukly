import { TestBed } from '@angular/core/testing';
import { GOVERNORATE_STORAGE_KEY, GovernorateMemory } from './governorate-memory';

describe('GovernorateMemory', () => {
  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('reads the governorate chosen during a previous visit', () => {
    localStorage.setItem(GOVERNORATE_STORAGE_KEY, 'sousse');

    expect(TestBed.inject(GovernorateMemory).governorate()).toBe('sousse');
  });

  it('remembers a choice and forgets it for "All of Tunisia"', () => {
    const memory = TestBed.inject(GovernorateMemory);

    memory.remember('sfax');
    expect(localStorage.getItem(GOVERNORATE_STORAGE_KEY)).toBe('sfax');
    expect(memory.governorate()).toBe('sfax');

    memory.remember(null);
    expect(localStorage.getItem(GOVERNORATE_STORAGE_KEY)).toBeNull();
    expect(memory.governorate()).toBeNull();
  });

  it('ignores a stored value that is not a governorate code', () => {
    localStorage.setItem(GOVERNORATE_STORAGE_KEY, '<script>');

    expect(TestBed.inject(GovernorateMemory).governorate()).toBeNull();
  });

  it('keeps working when the storage throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied');
    });
    const memory = TestBed.inject(GovernorateMemory);

    expect(memory.governorate()).toBeNull();
    memory.remember('tunis');
    expect(memory.governorate()).toBe('tunis');
  });
});
