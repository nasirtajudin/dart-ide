const { chromium } = require('playwright');

(async () => {
  const url = process.argv[2];
  if (!url) { console.error('usage: node check.js <url>'); process.exit(2); }
  const browser = await chromium.launch({
    args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader']
  });
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push('pageerror: ' + e));
  page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text()); });

  await page.goto(url, { waitUntil: 'load', timeout: 60000 });
  try {
    await page.waitForSelector('flutter-view, flt-glass-pane', { timeout: 120000 });
  } catch (e) {
    console.error('FAIL: flutter view never appeared');
    if (errors.length) console.error(errors.join('\n'));
    await browser.close();
    process.exit(1);
  }
  await page.waitForTimeout(4000);
  const fatal = errors.filter((e) => !/favicon|sourcemap|deprecat|autofocus/i.test(e));
  if (fatal.length > 0) {
    console.error('FAIL with page errors:\n' + fatal.join('\n'));
    await browser.close();
    process.exit(1);
  }
  console.log('SMOKE OK: flutter web view rendered without errors');
  await browser.close();
})();
