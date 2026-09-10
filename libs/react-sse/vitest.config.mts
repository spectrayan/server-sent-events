import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  resolve: {
    alias: {
      '@spectrayan/sse-client': path.resolve(__dirname, '../sse-client/src/index.ts'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['libs/react-sse/tests/**/*.test.{ts,tsx}'],
  },
});
