/** Photos uploaded at the same time (ANN-04). */
export const PARALLEL_UPLOADS = 3;

/**
 * Runs tasks with at most `limit` of them at the same time, in the order they were added. A task
 * that fails does not stop the others: the result of `add` rejects with its error.
 */
export class UploadQueue {
  private running = 0;
  private readonly waiting: (() => void)[] = [];

  constructor(private readonly limit = PARALLEL_UPLOADS) {}

  /** Tasks started and not finished yet. */
  get active(): number {
    return this.running;
  }

  add<T>(task: () => Promise<T>): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      const start = () => {
        this.running++;
        task()
          .then(resolve, reject)
          .finally(() => {
            this.running--;
            this.waiting.shift()?.();
          });
      };
      if (this.running < this.limit) {
        start();
      } else {
        this.waiting.push(start);
      }
    });
  }
}
