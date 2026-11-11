import { chromium } from "playwright";
import assert from "node:assert/strict";
import { mkdir } from "node:fs/promises";
import { createPreview } from "./preview.mjs";

const output = new URL("./artifacts/", import.meta.url);
await mkdir(output, { recursive: true });
const preview = await createPreview();
const browser = await chromium.launch({ headless: true });
try {
  for (const initial of ["light", "dark"]) {
    const context = await browser.newContext({ colorScheme: initial });
    const page = await context.newPage();
    for (const path of ["/", "/messages", "/login"]) {
      await page.emulateMedia({ colorScheme: initial });
      await page.goto(preview.url + path);
      await page.locator(".brand").waitFor();
      if (path === "/") await page.locator(".download").first().waitFor();
      if (path === "/messages") {
        await page.getByText("已连接", { exact: true }).waitFor();
        await page.getByLabel("消息内容").fill("# Theme\n\n> Quote\n\n`inline`\n\n```js\nconst example = 1;\n```\n\n[Link](https://example.com)\n\n| Name | Value |\n| --- | --- |\n| Theme | System |");
        await page.getByRole("button", { name: "发送", exact: true }).click();
        await page.locator(".markdown-body table").waitFor();
      }
      if (path === "/login") {
        await page.getByLabel("访问密码", { exact: true }).fill("wrong");
        await page.getByRole("button", { name: "连接", exact: true }).click();
        await page.getByText("密码不正确，请检查手机上的访问密码。").waitFor();
      }
      const checkContrast = async (scheme) => {
        await page.emulateMedia({ colorScheme: scheme });
        const result = await page.evaluate(() => {
          const rgb = (value) => value.match(/[\d.]+/g).slice(0, 3).map(Number);
          const luminance = (color) => rgb(color).map(v => {
            v /= 255;
            return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
          }).reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0);
          const background = (node) => {
            for (; node; node = node.parentElement) {
              const color = getComputedStyle(node).backgroundColor;
              if (color !== "rgba(0, 0, 0, 0)" && color !== "transparent") return color;
            }
            throw new Error("No effective background");
          };
          const failures = [];
          for (const node of document.querySelectorAll("a, p, h1, h2, th, td, label, input, textarea, button, small, code, blockquote, .count, .connection-title, .topbar")) {
            if (!node.getClientRects().length || node.disabled) continue;
            const style = getComputedStyle(node);
            const foreground = luminance(style.color);
            const bg = luminance(background(node));
            const ratio = (Math.max(foreground, bg) + 0.05) / (Math.min(foreground, bg) + 0.05);
            if (ratio < 4.5) failures.push({ tag: node.tagName, class: node.className, ratio });
            if (node.matches("input, textarea")) {
              const placeholder = luminance(getComputedStyle(node, "::placeholder").color);
              const placeholderRatio = (Math.max(placeholder, bg) + 0.05) / (Math.min(placeholder, bg) + 0.05);
              if (placeholderRatio < 4.5) failures.push({ tag: node.tagName, placeholderRatio });
            }
          }
          return { background: getComputedStyle(document.documentElement).backgroundColor, failures, errorColors: [...document.querySelectorAll(".error")].map(node => getComputedStyle(node).color) };
        });
        assert.equal(result.background, scheme === "dark" ? "rgb(20, 25, 20)" : "rgb(252, 253, 246)");
        assert.deepEqual(result.failures, [], `${path} ${scheme} text contrast`);
        for (const color of result.errorColors) {
          assert.equal(color, scheme === "dark" ? "rgb(255, 180, 171)" : "rgb(186, 26, 26)");
        }
      };
      for (const scheme of [initial, initial === "dark" ? "light" : "dark", initial]) {
        await checkContrast(scheme);
        for (const control of await page.locator(".nav a, .button, .download, input:not([type=hidden]), textarea").all()) {
          if (!(await control.isVisible()) || !(await control.isEnabled())) continue;
          await control.hover();
          await checkContrast(scheme);
          await page.keyboard.press("Tab");
          await control.focus();
          assert.equal(await control.evaluate(node => getComputedStyle(node).outlineStyle), "solid");
        }
        await page.mouse.move(0, 0);
        if (path === "/") {
          preview.state.failure = true;
          preview.refresh();
          await page.getByText("暂时无法加载").waitFor();
          assert.equal(await page.locator("#file-notice p").getAttribute("class"), "error");
          await checkContrast(scheme);
          preview.state.failure = false;
          await page.getByRole("button", { name: "重新加载" }).click();
          await page.locator(".download").first().waitFor();
        }
      }
      await page.screenshot({ path: new URL(`theme-${initial}-${path === "/" ? "files" : path.slice(1)}.png`, output).pathname, fullPage: true });
      await page.reload();
      assert.equal(await page.evaluate(() => matchMedia("(prefers-color-scheme: dark)").matches), initial === "dark");
      await checkContrast(initial);
    }
    await context.close();
  }
  console.log("PASS: files/messages/login initial themes, live bidirectional changes, reload and rendered text contrast >=4.5:1.");
} finally {
  await browser.close();
  await preview.close();
}
