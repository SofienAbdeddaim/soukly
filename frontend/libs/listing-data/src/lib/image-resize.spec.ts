import { DecodedImage, fitWithin, JPEG_QUALITY, resizeForUpload, Size } from './image-resize';

/** A decoder that reports the given size and records the JPEG exports asked of it. */
function fakeDecoder(size: Size) {
  const exports: { size: Size; quality: number }[] = [];
  const close = vi.fn();
  const decode = async (): Promise<DecodedImage> => ({
    ...size,
    close,
    toJpeg: async (target, quality) => {
      exports.push({ size: target, quality });
      return new Blob(['jpeg'], { type: 'image/jpeg' });
    },
  });
  return { decode, exports, close };
}

function file(bytes: number, type = 'image/jpeg'): File {
  return new File([new Uint8Array(bytes)], 'photo.jpg', { type });
}

describe('fitWithin', () => {
  it('brings the longest side to 1600 px, ratio kept', () => {
    expect(fitWithin({ width: 4000, height: 3000 })).toEqual({ width: 1600, height: 1200 });
    expect(fitWithin({ width: 3000, height: 4000 })).toEqual({ width: 1200, height: 1600 });
  });

  it('keeps a smaller photo as it is', () => {
    expect(fitWithin({ width: 1200, height: 800 })).toEqual({ width: 1200, height: 800 });
  });
});

describe('resizeForUpload', () => {
  it('exports a 4000 × 3000 photo of 4 MB as a JPEG of 1600 × 1200', async () => {
    const decoder = fakeDecoder({ width: 4000, height: 3000 });

    const result = await resizeForUpload(file(4 * 1024 * 1024), decoder.decode);

    expect(result.type).toBe('image/jpeg');
    expect(decoder.exports).toEqual([
      { size: { width: 1600, height: 1200 }, quality: JPEG_QUALITY },
    ]);
    expect(decoder.close).toHaveBeenCalled();
  });

  it('sends a small photo as it is', async () => {
    const original = file(300_000, 'image/png');
    const decoder = fakeDecoder({ width: 1024, height: 768 });

    expect(await resizeForUpload(original, decoder.decode)).toBe(original);
    expect(decoder.exports).toEqual([]);
  });

  it('exports a heavy photo of small size at its own size', async () => {
    const decoder = fakeDecoder({ width: 1500, height: 1000 });

    await resizeForUpload(file(6 * 1024 * 1024, 'image/png'), decoder.decode);

    expect(decoder.exports[0].size).toEqual({ width: 1500, height: 1000 });
  });

  it('falls back to the original below 5 MB when the browser cannot decode it', async () => {
    const original = file(2 * 1024 * 1024);
    const failing = () => Promise.reject(new Error('cannot decode'));

    expect(await resizeForUpload(original, failing)).toBe(original);
    await expect(resizeForUpload(file(6 * 1024 * 1024), failing)).rejects.toThrow('cannot decode');
  });
});
