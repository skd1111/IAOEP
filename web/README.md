# IAOEP Web Dashboard (React 19 + Vite)

Agent 可观测性可视化界面。实时 trace 列表、详情树、metrics 总览。

## 技术栈

- **React 19** + **TypeScript 5.7**
- **Vite 6** (构建工具)
- **Tailwind CSS 3** (样式)
- **shadcn/ui** (基础组件,基于 Radix UI)
- **TanStack Router** + **TanStack Query** (路由 + 数据获取)
- **Recharts** (图表)
- **lucide-react** (图标)

## 开发

```bash
cd web
pnpm install     # 或 npm install
pnpm dev         # 启动 Vite dev server (端口 5173)
```

浏览器打开 http://localhost:5173

Vite dev server 会代理 `/api/v1/*` 请求到 backend (默认 `http://localhost:8081`)。

## 构建

```bash
pnpm build       # 输出到 dist/
pnpm preview     # 本地预览生产构建
```

构建产物是静态文件,可由 Nginx / Caddy / 任何静态服务器托管。

## Docker

```bash
docker build -t iaoep/web:0.1.0 .
docker run -p 5173:80 iaoep/web:0.1.0
```

## 项目结构

```
web/
├── src/
│   ├── main.tsx              # 入口
│   ├── App.tsx               # 路由配置
│   ├── index.css             # Tailwind + 主题变量
│   ├── components/
│   │   ├── ui/              # shadcn/ui 组件
│   │   │   ├── button.tsx
│   │   │   └── card.tsx
│   │   └── layout/
│   │       └── Sidebar.tsx
│   ├── lib/
│   │   ├── utils.ts         # cn() / format helpers
│   │   ├── api.ts           # API client + mock
│   │   └── types.ts         # 类型定义
│   └── pages/
│       ├── Dashboard.tsx     # /dashboard
│       ├── TraceList.tsx     # /traces
│       ├── TraceDetail.tsx   # /traces/:id
│       └── EvaluationList.tsx # /evaluations
├── public/
├── package.json
├── vite.config.ts
├── tailwind.config.js
├── postcss.config.js
├── tsconfig.json
├── index.html
└── README.md
```

## Phase 1 状态

| 页面 | 状态 | 说明 |
|---|---|---|
| Dashboard | ✅ 完整 | 9 个 metrics 卡片 + 30s 自动刷新 |
| Trace 列表 | ✅ 完整 | 过滤 + 列表 + 跳转详情 |
| Trace 详情 | ✅ 完整 | Span 树状图 + attributes |
| 评测列表 | ⏳ 占位 | Phase 2 实现 |
| 实时 trace 流 | ⏳ 占位 | Phase 2 (SSE / WebSocket) |

当前用 mock 数据 (Phase 1 backend 还在并行开发)。完整 backend API 完成后切换到 `api.xxx()` 调用。

## 添加新 shadcn/ui 组件

```bash
# 用 shadcn CLI (需要先 npx shadcn@latest init)
npx shadcn@latest add dialog dropdown-menu tabs
```

## License

[MIT](../../LICENSE)
