import { expect, Page } from '@playwright/test';

interface AxeViolation {
  id: string;
  impact: string | null;
  nodes: { target: string[] }[];
}

/** Runs axe-core on the page and fails on any `serious` or `critical` violation. */
export async function expectNoSeriousA11yViolations(page: Page): Promise<void> {
  await page.addScriptTag({ path: require.resolve('axe-core') });
  const violations = await page.evaluate(async () => {
    const axe = (window as unknown as { axe: { run: () => Promise<{ violations: unknown[] }> } })
      .axe;
    return (await axe.run()).violations;
  });
  const blocking = (violations as AxeViolation[])
    .filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
    .map(
      (violation) => `${violation.id}: ${violation.nodes.map((node) => node.target).join(', ')}`,
    );
  expect(blocking).toEqual([]);
}
