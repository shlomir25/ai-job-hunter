import { test, expect } from '@playwright/test'

test('app loads and shows the review queue route', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('Review queue', { exact: false })).toBeVisible()
})
