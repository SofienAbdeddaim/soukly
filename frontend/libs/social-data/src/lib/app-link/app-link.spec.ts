import { appLinkFor, detectLinkPlatform } from './app-link';

const UA = {
  androidChrome:
    'Mozilla/5.0 (Linux; Android 14; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36',
  androidTablet:
    'Mozilla/5.0 (Linux; Android 13; SM-X200) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
  androidWebView:
    'Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.0.0 Mobile Safari/537.36',
  iphone:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1',
  ipadOs:
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15',
  facebookApp:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 [FBAN/FBIOS;FBAV/490.0.0.0]',
  instagramApp:
    'Mozilla/5.0 (Linux; Android 14; SM-A546B; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.0.0 Mobile Safari/537.36 Instagram 350.0.0.0',
  windowsChrome:
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
};

describe('detectLinkPlatform', () => {
  it.each([
    ['an Android phone', UA.androidChrome, 0, 'android'],
    ['an Android tablet', UA.androidTablet, 5, 'android'],
    ['an iPhone', UA.iphone, 5, 'ios'],
    ['an iPad that says it is a Mac', UA.ipadOs, 5, 'ios'],
    ['a Mac', UA.ipadOs, 0, 'desktop'],
    ['Windows', UA.windowsChrome, 0, 'desktop'],
    ['the Facebook in-app browser', UA.facebookApp, 5, 'in-app'],
    ['the Instagram in-app browser', UA.instagramApp, 5, 'in-app'],
    ['an Android web view', UA.androidWebView, 5, 'in-app'],
  ])('detects %s', (_, userAgent, maxTouchPoints, platform) => {
    expect(detectLinkPlatform({ userAgent, maxTouchPoints })).toBe(platform);
  });

  it('takes an unknown browser as desktop', () => {
    expect(detectLinkPlatform(undefined)).toBe('desktop');
  });
});

describe('appLinkFor', () => {
  const POST = 'https://www.facebook.com/61550000000000/posts/pfbid02abc?ref=soukly';
  const REEL = 'https://www.instagram.com/p/DAbCdEf123/';

  it('keeps the link in a new tab on desktop', () => {
    expect(appLinkFor(POST, 'desktop')).toEqual({ href: POST, target: '_blank' });
  });

  it('opens a Facebook post in the Facebook app on Android, with the web page as fallback', () => {
    expect(appLinkFor(POST, 'android')).toEqual({
      href:
        'intent://www.facebook.com/61550000000000/posts/pfbid02abc?ref=soukly' +
        '#Intent;scheme=https;package=com.facebook.katana;S.browser_fallback_url=' +
        encodeURIComponent(POST) +
        ';end',
      target: null,
    });
  });

  it('opens an Instagram post in the Instagram app on Android', () => {
    const { href } = appLinkFor(REEL, 'android');
    expect(href).toMatch(/^intent:\/\/www\.instagram\.com\/p\/DAbCdEf123\/#Intent;scheme=https;/);
    expect(href).toContain(';package=com.instagram.android;');
  });

  it.each([
    'https://m.facebook.com/story.php?id=1',
    'https://fb.me/abc',
    'https://instagram.com/soukly',
  ])('knows the host of %s', (url) => {
    expect(appLinkFor(url, 'android').href).toMatch(/^intent:\/\//);
  });

  it('keeps the plain link in the same tab on iOS, for universal links', () => {
    expect(appLinkFor(POST, 'ios')).toEqual({ href: POST, target: null });
  });

  it('keeps the plain link in an in-app browser', () => {
    expect(appLinkFor(POST, 'in-app')).toEqual({ href: POST, target: null });
  });

  it.each([
    'https://notfacebook.com/page',
    'https://www.facebook.com.evil.example/page',
    'http://www.facebook.com/page',
    'not a url',
  ])('leaves %s as it is on Android', (url) => {
    expect(appLinkFor(url, 'android')).toEqual({ href: url, target: null });
  });
});
