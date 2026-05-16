# Vue 3 + Vite

This template should help get you started developing with Vue 3 in Vite. The template uses Vue 3 `<script setup>` SFCs, check out the [script setup docs](https://v3.vuejs.org/api/sfc-script-setup.html#sfc-script-setup) to learn more.

Learn more about IDE Support for Vue in the [Vue Docs Scaling up Guide](https://vuejs.org/guide/scaling-up/tooling.html#ide-support).

## API proxy

Dev server proxies `/api` and `/chat` to backend.

Default backend target:

```bash
http://localhost:8080
```

Override target by adding `.env.local` in this folder:

```bash
VITE_API_TARGET=http://localhost:8081
```
