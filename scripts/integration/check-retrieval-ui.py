from pathlib import Path
from playwright.sync_api import sync_playwright, expect

output = Path(__file__).resolve().parents[2] / "artifacts/reliability/visual-qa"
output.mkdir(parents=True, exist_ok=True)
with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    try:
        for name, width, height in [("desktop", 1440, 1000), ("mobile", 390, 844)]:
            page = browser.new_page(viewport={"width": width, "height": height})
            page.goto("http://127.0.0.1:5178/tests/fixtures/retrieval-qa.html")
            page.wait_for_load_state("networkidle")
            summary = page.locator("summary")
            expect(summary).to_have_count(1)
            summary.focus()
            page.keyboard.press("Enter")
            expect(page.locator("details")).to_have_attribute("open", "")
            expect(page.get_by_text("来源不可用", exact=False)).to_be_visible()
            expect(page.get_by_role("button", name="查看当前文档")).to_have_count(1)
            assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), "Horizontal overflow"
            page.screenshot(path=str(output / f"retrieval-{name}.png"), full_page=True)
            page.close()
        print("RETRIEVAL_COMPONENT_RENDER=PASS desktop=1440x1000 mobile=390x844 keyboard=PASS")
    finally:
        browser.close()
