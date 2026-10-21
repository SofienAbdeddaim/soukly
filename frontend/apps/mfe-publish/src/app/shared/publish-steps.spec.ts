import { draftListing } from '@soukly/listing-data';
import {
  firstIncompleteStep,
  nextStep,
  previousStep,
  stepNumber,
  stepOfField,
} from './publish-steps';

describe('publish steps', () => {
  it('numbers the steps in the validated order', () => {
    expect(stepNumber('category')).toBe(1);
    expect(stepNumber('preview')).toBe(5);
    expect(previousStep('photos')).toBe('category');
    expect(previousStep('category')).toBeNull();
    expect(nextStep('details')).toBe('price-location');
  });

  it('finds the step of every missing field', () => {
    expect(stepOfField('attributes.year')).toBe('details');
    expect(stepOfField('governorateCode')).toBe('price-location');
    expect(stepOfField('photos')).toBe('photos');
  });

  it('resumes a draft on its first incomplete step', () => {
    expect(firstIncompleteStep(draftListing())).toBe('photos');
    expect(firstIncompleteStep(draftListing({ missingFields: ['title', 'priceMode'] }))).toBe(
      'details',
    );
    expect(firstIncompleteStep(draftListing({ missingFields: [] }))).toBe('preview');
  });
});
