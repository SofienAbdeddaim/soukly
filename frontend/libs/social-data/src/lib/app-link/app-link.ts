/** Where the visitor is browsing, as far as opening a social network link goes. */
export type LinkPlatform = 'android' | 'ios' | 'in-app' | 'desktop';

/** The attributes an outbound social network link gets on a given platform. */
export interface AppLinkAttributes {
  href: string;
  /** `_blank` on desktop; none on phones and tablets, so the operating system can open the app. */
  target: '_blank' | null;
}

/** The Android app that opens the links of each social network host. */
const ANDROID_PACKAGES: readonly { host: RegExp; androidPackage: string }[] = [
  { host: /(^|\.)(facebook\.com|fb\.com|fb\.me)$/i, androidPackage: 'com.facebook.katana' },
  { host: /(^|\.)instagram\.com$/i, androidPackage: 'com.instagram.android' },
];

/** The in-app browsers of Facebook, Instagram and Messenger, and Android web views. */
const IN_APP_BROWSER = /FBAN|FBAV|FB_IAB|FBIOS|Instagram|; wv\)/i;

/**
 * Guesses the platform from the browser. iPadOS Safari says it is a Mac, so a Mac with a touch
 * screen is taken as an iPad.
 */
export function detectLinkPlatform(
  navigator: Pick<Navigator, 'userAgent' | 'maxTouchPoints'> | undefined,
): LinkPlatform {
  const userAgent = navigator?.userAgent ?? '';
  if (IN_APP_BROWSER.test(userAgent)) {
    return 'in-app';
  }
  if (/Android/i.test(userAgent)) {
    return 'android';
  }
  if (
    /iPhone|iPad|iPod/i.test(userAgent) ||
    (/Macintosh/i.test(userAgent) && (navigator?.maxTouchPoints ?? 0) > 1)
  ) {
    return 'ios';
  }
  return 'desktop';
}

/**
 * How to open a Facebook or Instagram link so that a member who uses the app lands in it, where
 * they are signed in, instead of in the mobile browser:
 * - desktop: the link as it is, in a new tab;
 * - Android: an `intent://` link to the app of the network, falling back to the web page when the
 *   app is not installed;
 * - iOS and iPadOS: the plain `https` link in the same tab, which iOS hands to the app through its
 *   universal links (a new tab can keep the page in Safari);
 * - in-app browsers (the page opened from Facebook or Instagram): the plain link, the member is
 *   already in the app and a web view cannot open `intent://` links.
 * Any other link, or a link that is not a valid `https` address, is left as it is.
 */
export function appLinkFor(url: string, platform: LinkPlatform): AppLinkAttributes {
  if (platform === 'desktop') {
    return { href: url, target: '_blank' };
  }
  if (platform !== 'android') {
    return { href: url, target: null };
  }
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return { href: url, target: null };
  }
  const app = ANDROID_PACKAGES.find(({ host }) => host.test(parsed.hostname));
  if (parsed.protocol !== 'https:' || !app) {
    return { href: url, target: null };
  }
  const path = `${parsed.host}${parsed.pathname}${parsed.search}`;
  const fallback = encodeURIComponent(parsed.href);
  return {
    href: `intent://${path}#Intent;scheme=https;package=${app.androidPackage};S.browser_fallback_url=${fallback};end`,
    target: null,
  };
}
