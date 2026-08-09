import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Quinoa proxies this dev server through Quarkus (8080) in dev mode.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
});
