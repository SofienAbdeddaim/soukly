import { UploadQueue } from './upload-queue';

const settle = () => new Promise((resolve) => setTimeout(resolve));

/** A task that ends when the test says so. */
function deferred() {
  let resolve!: () => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<void>((ok, ko) => {
    resolve = ok;
    reject = ko;
  });
  return { promise, resolve, reject };
}

describe('UploadQueue', () => {
  it('runs at most 3 uploads at the same time, the next one when one ends', async () => {
    const queue = new UploadQueue();
    const tasks = Array.from({ length: 5 }, deferred);
    const started: number[] = [];
    tasks.forEach((task, index) =>
      queue.add(() => {
        started.push(index);
        return task.promise;
      }),
    );

    expect(started).toEqual([0, 1, 2]);
    expect(queue.active).toBe(3);

    tasks[1].resolve();
    await settle();
    expect(started).toEqual([0, 1, 2, 3]);
  });

  it('goes on after a failure, which only rejects its own result', async () => {
    const queue = new UploadQueue(1);
    const failing = queue.add(() => Promise.reject(new Error('network')));
    const next = queue.add(() => Promise.resolve('done'));

    await expect(failing).rejects.toThrow('network');
    expect(await next).toBe('done');
    expect(queue.active).toBe(0);
  });

  it('takes a retried upload back in the queue', async () => {
    const queue = new UploadQueue(1);
    let attempts = 0;
    const upload = () => {
      attempts++;
      return attempts === 1 ? Promise.reject(new Error('network')) : Promise.resolve('ok');
    };
    await expect(queue.add(upload)).rejects.toThrow();
    expect(await queue.add(upload)).toBe('ok');
  });
});
