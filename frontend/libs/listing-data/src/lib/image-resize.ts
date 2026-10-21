import { MAX_PHOTO_BYTES } from './models';

/** Longest side of a photo sent to the storage; the server converts it to WebP (ANN-02). */
export const MAX_PHOTO_SIDE = 1600;

/** JPEG quality of a reduced photo: a format every browser exports. */
export const JPEG_QUALITY = 0.85;

export interface Size {
  width: number;
  height: number;
}

/** Size of a photo once its longest side is at most `max`, ratio kept; unchanged when smaller. */
export function fitWithin(size: Size, max = MAX_PHOTO_SIDE): Size {
  const longest = Math.max(size.width, size.height);
  if (longest <= max) {
    return { width: size.width, height: size.height };
  }
  const scale = max / longest;
  return {
    width: Math.max(1, Math.round(size.width * scale)),
    height: Math.max(1, Math.round(size.height * scale)),
  };
}

/** A decoded image: its size, and a way to draw it at another size and export it as JPEG. */
export interface DecodedImage extends Size {
  toJpeg(size: Size, quality: number): Promise<Blob>;
  close(): void;
}

/** Decodes a file, with its EXIF orientation applied; rejects when the browser cannot. */
export type ImageDecoder = (file: Blob) => Promise<DecodedImage>;

/**
 * The file to upload for a photo: reduced in the browser to at most 1600 px on its longest side
 * and exported as JPEG (quality 0.85), or the file itself when it is already small enough (in
 * pixels and below 5 MB; a heavy small file is exported as JPEG at its own size). When
 * the browser cannot decode the file, the original is sent if it is below the 5 MB limit of the
 * API (the server checks it anyway); otherwise the promise rejects.
 */
export async function resizeForUpload(
  file: File,
  decode: ImageDecoder = decodeWithCanvas,
): Promise<Blob> {
  let image: DecodedImage;
  try {
    image = await decode(file);
  } catch (error) {
    if (file.size < MAX_PHOTO_BYTES) {
      return file;
    }
    throw error;
  }
  try {
    const target = fitWithin(image);
    const fits = target.width === image.width && target.height === image.height;
    if (fits && file.size < MAX_PHOTO_BYTES) {
      return file;
    }
    return await image.toJpeg(target, JPEG_QUALITY);
  } finally {
    image.close();
  }
}

/** `createImageBitmap` + `OffscreenCanvas` (or a `canvas` element where it does not exist). */
export const decodeWithCanvas: ImageDecoder = async (file) => {
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  return {
    width: bitmap.width,
    height: bitmap.height,
    close: () => bitmap.close(),
    toJpeg: async ({ width, height }, quality) => {
      if (typeof OffscreenCanvas !== 'undefined') {
        const canvas = new OffscreenCanvas(width, height);
        drawOn(canvas.getContext('2d'), bitmap, width, height);
        return canvas.convertToBlob({ type: 'image/jpeg', quality });
      }
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      drawOn(canvas.getContext('2d'), bitmap, width, height);
      return new Promise<Blob>((resolve, reject) =>
        canvas.toBlob(
          (blob) => (blob ? resolve(blob) : reject(new Error('JPEG export failed'))),
          'image/jpeg',
          quality,
        ),
      );
    },
  };
};

function drawOn(
  context: CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D | null,
  bitmap: ImageBitmap,
  width: number,
  height: number,
): void {
  if (!context) {
    throw new Error('2D canvas unavailable');
  }
  context.imageSmoothingQuality = 'high';
  context.drawImage(bitmap, 0, 0, width, height);
}
