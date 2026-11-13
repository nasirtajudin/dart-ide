const { chromium } = require('playwright');

// Verbose version of check.js: dumps every console message, failed request,
// 4xx response and page error with stack. Swap it into fetch-flutter-web.sh
// (stage 5/6) when a smoke failure needs diagnosing.
(async () => {
  const url = process.argv[2];
  const browser = await chromium.launch({
    args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader']
  });
  const page = await browser.newPage();
  page.on('pageerror', (e) => console.error('[PAGEERROR]', e && e.stack ? e.stack : String(e)));
  page.on('console', (m) => console.log('[console.' + m.type() + ']', m.text().slice(0, 500)));
  page.on('requestfailed', (r) => console.error('[REQFAIL]', r.url(), '->', r.failure() && r.failure().errorText));
  page.on('response', (r) => { if (r.status() >= 400) console.error('[HTTP' + r.status() + ']', r.url()); });
  await page.goto(url, { waitUntil: 'load', timeout: 60000 });
  try {
    await page.waitForSelector('flutter-view, flt-glass-pane', { timeout: 30000 });
    console.log('OK: flutter view appeared');
  } catch (e) {
    console.error('NO flutter-view after 30s');
  }
  await page.waitForTimeout(3000);
  await browser.close();
})();
