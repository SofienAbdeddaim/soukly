import { APIRequestContext, expect } from '@playwright/test';

/** Mailpit of docker-compose.yml: HTTP API on 8025 (https://mailpit.axllent.org/docs/api-v1/). */
const MAILPIT_URL = process.env['MAILPIT_URL'] ?? 'http://localhost:8025';

/**
 * The verification link of the last email sent to an address, waiting for the email to arrive
 * (user-service sends it after the transaction, on another thread).
 */
export async function verificationLink(request: APIRequestContext, to: string): Promise<string> {
  let link: string | undefined;
  await expect(async () => {
    const search = await request.get(`${MAILPIT_URL}/api/v1/search`, {
      params: { query: `to:"${to}"`, limit: '1' },
    });
    expect(search.ok()).toBe(true);
    const { messages } = (await search.json()) as { messages: { ID: string }[] };
    expect(messages.length).toBeGreaterThan(0);
    const message = await request.get(`${MAILPIT_URL}/api/v1/message/${messages[0].ID}`);
    const { Text } = (await message.json()) as { Text: string };
    link = Text.match(/https?:\/\/\S+\/account\/verify-email\?token=[\w-]+/)?.[0];
    expect(link).toBeDefined();
  }).toPass({ timeout: 15_000 });
  return link as string;
}
