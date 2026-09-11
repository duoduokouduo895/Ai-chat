from pathlib import Path

from playwright.sync_api import expect, sync_playwright


output = Path(__file__).resolve().parents[2] / "artifacts/reliability/visual-qa"
output.mkdir(parents=True, exist_ok=True)

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    try:
        for name, width, height in [("desktop", 1440, 1000), ("mobile", 390, 844)]:
            normal = browser.new_page(viewport={"width": width, "height": height})
            normal.route(
                "**/api/knowledge/cleanup-operations/**",
                lambda route: route.fulfill(
                    status=200,
                    content_type="application/json",
                    body='{"operationId":"cleanup-doc-demo","status":"NEEDS_ATTENTION","step":1,"attempt":8,"errorCode":"CLEANUP_STEP_FAILED"}',
                ),
            )
            normal.goto("http://127.0.0.1:5178/tests/fixtures/cleanup-qa.html")
            normal.wait_for_load_state("networkidle")
            expect(normal.get_by_text("已移除，后台清理中。", exact=True)).to_be_visible()
            progress = normal.get_by_role("button", name="查看清理进度")
            progress.focus()
            normal.keyboard.press("Enter")
            expect(normal.get_by_text("管理员可重试", exact=False)).to_be_visible()
            assert normal.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
            normal.screenshot(path=str(output / f"cleanup-user-{name}.png"), full_page=True)
            normal.close()

            admin = browser.new_page(viewport={"width": width, "height": height})

            def admin_api(route):
                if route.request.method == "POST":
                    route.fulfill(
                        status=200,
                        content_type="application/json",
                        body='{"operationId":"cleanup-kb-demo","status":"PENDING","attempt":0}',
                    )
                else:
                    route.fulfill(
                        status=200,
                        content_type="application/json",
                        body='[{"operationId":"cleanup-kb-demo","status":"NEEDS_ATTENTION","attempt":8,"errorCode":"CLEANUP_STEP_FAILED"}]',
                    )

            admin.route("**/api/knowledge/cleanup-operations**", admin_api)
            admin.goto("http://127.0.0.1:5179/tests/fixtures/cleanup-qa.html")
            admin.wait_for_load_state("networkidle")
            expect(admin.get_by_text("清理暂未完成", exact=True)).to_be_visible()
            retry = admin.get_by_role("button", name="重试清理")
            retry.focus()
            admin.keyboard.press("Enter")
            expect(admin.get_by_text("已受理重试", exact=False)).to_be_visible()
            assert admin.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
            admin.screenshot(path=str(output / f"cleanup-admin-{name}.png"), full_page=True)
            admin.close()
        print("CLEANUP_COMPONENT_RENDER=PASS desktop=1440x1000 mobile=390x844 keyboard=PASS")
    finally:
        browser.close()
