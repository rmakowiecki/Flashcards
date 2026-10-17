// Runs the wasmJs unit tests in the Chromium that puppeteer downloaded, not an installed Chrome.
// This config runs synchronously but puppeteer only resolves the path asynchronously. That is
// enough: the launcher reads CHROME_BIN when it starts the browser, after the test bundle is built.
require("puppeteer")
    .executablePath()
    .then((path) => {
        process.env.CHROME_BIN = path;
    });

// CI build machines often lack the kernel features Chrome's sandbox needs; the sandbox guards
// against hostile pages, which these tests never load.
if (process.env.CI) {
    config.set({
        browsers: ["ChromeHeadlessCI"],
        customLaunchers: {
            ChromeHeadlessCI: { base: "ChromeHeadless", flags: ["--no-sandbox"] },
        },
    });
}
