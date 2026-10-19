// Runs the wasmJs unit tests in the Chromium that puppeteer downloaded, not an installed Chrome.
// puppeteer only resolves the path asynchronously, and under a loaded build the launcher can start
// the browser before a promise here settles. A child node process resolves it, so CHROME_BIN is set
// before this config returns.
const puppeteerModule = JSON.stringify(require.resolve("puppeteer"));
process.env.CHROME_BIN = require("child_process").execFileSync(
    process.execPath,
    ["-e", `require(${puppeteerModule}).executablePath().then((path) => process.stdout.write(path))`],
    { encoding: "utf8" },
);

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
