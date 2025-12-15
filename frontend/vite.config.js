import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/ws': {
        target: 'http://localhost:8099',
        changeOrigin: false,
      },
      '/app': {
        target: 'http://localhost:8099',
        changeOrigin: false,
      },
      '/topic': {
        target: 'http://localhost:8099',
        changeOrigin: false,
      },
      '/user': {
        target: 'http://localhost:8099',
        changeOrigin: false,
      },
      '/api': {
        target: 'http://localhost:8099',
        changeOrigin: false,
      },
      '/login': {
        target: 'http://localhost:8099',
        changeOrigin: false,
        bypass: (req, res, options) => {
          if (req.method === 'GET') {
            return req.url;
          }
        }
      },
      '/register': {
        target: 'http://localhost:8099',
        changeOrigin: false,
        bypass: (req, res, options) => {
          if (req.method === 'GET') {
            return req.url;
          }
        }
      },
      '/account': {
        target: 'http://localhost:8099',
        changeOrigin: false,
        bypass: (req, res, options) => {
          if (req.method === 'GET') {
            return req.url;
          }
        }
      },
      '/game': {
        target: 'http://localhost:8099',
        changeOrigin: false,
        bypass: (req, res, options) => {
          if (req.method === 'GET') {
            return req.url;
          }
        }
      },
      '/img': {
        target: 'http://localhost:8099',
        changeOrigin: false,
      },
      '/logout': {
        target: 'http://localhost:8099',
        changeOrigin: false,
      }
    }
  },
  define: {
    global: 'window'
  }
})
