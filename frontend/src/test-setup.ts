import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { setupServer } from 'msw/node'
import type { RequestHandler } from 'msw'
import { http, HttpResponse } from 'msw'

export const handlers: RequestHandler[] = []
export const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

export { http, HttpResponse }
