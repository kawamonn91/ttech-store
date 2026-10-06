# T-tech Store — Web

`store.kawamonn.com` の公開サイト・管理コンソール・API(`/api/v1`)。Next.js(App Router)。
全体の説明はリポジトリ直下の [README.md](../README.md)、セットアップは [docs/setup.md](../docs/setup.md) を参照。

```bash
npm install
npm run dev          # http://localhost:3000(環境変数は .env.local。項目は .env.example)
npm test             # Vitest
npm run typecheck
npm run build
```

本番は自前のサーバーで `next start` を PM2 で常駐させ、Cloudflare Tunnel で公開している。
反映は main に push してから `scripts/deploy-web.ps1`(手順は docs/setup.md の 4)。
