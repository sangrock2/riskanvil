import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

function createApiProxy(target) {
  return {
    target,
    changeOrigin: true,
    configure(proxy) {
      proxy.on("proxyReq", (proxyReq) => {
        try {
          const origin = new URL(target).origin;
          proxyReq.setHeader("Origin", origin);
          proxyReq.setHeader("Referer", `${origin}/`);
        } catch {
          // Ignore malformed proxy targets and fall back to default proxy behavior.
        }
      });
    },
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const proxyTarget = env.VITE_API_PROXY_TARGET || "http://localhost:8080";

  return {
    plugins: [react()],
    esbuild: {
      loader: "jsx",
      include: /src\/.*\.[jt]sx?$/,
      exclude: [],
    },
    optimizeDeps: {
      esbuildOptions: {
        loader: {
          ".js": "jsx",
        },
      },
    },
    server: {
      host: "0.0.0.0",
      port: 3000,
      proxy: {
        "/api": createApiProxy(proxyTarget),
        "/ws": {
          target: proxyTarget,
          ws: true,
          changeOrigin: true,
        },
      },
    },
    preview: {
      host: "0.0.0.0",
      port: 3000,
    },
    test: {
      environment: "jsdom",
      globals: true,
      setupFiles: "./src/setupTests.js",
      css: true,
    },
  };
});
