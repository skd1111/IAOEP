# Contributing to IAOEP

感谢你对 IAOEP 的关注!我们欢迎所有形式的贡献 — Bug 报告、Feature 请求、文档改进、代码 PR、翻译、推广。

---

## 🌟 贡献者路径

| 阶段 | 角色 | 权限 | 路径 |
|---|---|---|---|
| 入门 | **User** | 使用 / Issue 反馈 | 直接用即可 |
| 第一步 | **Contributor** | 提 PR / 评论 | 完成 1 个 PR 合并 |
| 进阶 | **Triage** | 分类 Issue / Review PR | 持续贡献 3 个月 |
| 核心 | **Maintainer** | Approve PR / Release | 由现任 Maintainer 提名 |
| 治理 | **Owner** | 战略决策 / 解散组织 | 项目发起人 |

---

## 🐛 报告 Bug

在提 Issue 前:

1. 搜索现有 Issues 避免重复
2. 升级到最新版本 (`main` 分支) 确认问题仍然存在
3. 收集关键信息:
   - IAOEP 版本 (commit hash 或 release tag)
   - 部署模式 (Docker Compose / K8s / 源码)
   - 操作系统 + Java/Go 版本
   - 复现步骤
   - 期望行为 vs 实际行为
   - 关键日志 (脱敏)

使用 [Bug Report 模板](.github/ISSUE_TEMPLATE/bug_report.md)。

---

## 💡 提出 Feature

使用 [Feature Request 模板](.github/ISSUE_TEMPLATE/feature_request.md),说明:
- **问题**: 当前痛点
- **方案**: 你想要的 (具体 / 模糊都可以)
- **替代**: 考虑过的其他方案
- **影响**: 谁会用,多少人受益

重大特性建议先在 Discussions 发起 **RFC**,获得社区反馈后再写代码。

---

## 🔧 提 PR

### 开发流程

```bash
# 1. Fork & Clone
git clone https://github.com/<your>/iaoep.git
cd iaoep

# 2. 创建分支 (命名规范)
git checkout -b feat/<short-desc>      # 新功能
git checkout -b fix/<short-desc>       # 修 bug
git checkout -b docs/<short-desc>       # 文档
git checkout -b refactor/<short-desc>   # 重构

# 3. 开发 (遵循各子模块 README)
cd ingest && go test ./...              # Go 服务
cd storage && mvn test                  # Java 服务
cd web && pnpm test                     # 前端

# 4. 提交 (Commit 规范)
git commit -m "feat(ingest): add OTLP HTTP receiver" -m ""
#   type(scope): subject
#   feat / fix / docs / refactor / test / chore
#   subject 50 字以内,动词开头

# 5. 推送 & 提 PR
git push origin feat/...
# 在 GitHub 提 PR,使用 [PR 模板](.github/PULL_REQUEST_TEMPLATE.md)
```

### PR 要求

- ✅ 通过 CI (build + lint + test)
- ✅ 至少 1 个 Maintainer Approve
- ✅ 重大变更 (架构调整 / 协议变更) 需 2 个 Maintainer Approve + 1 周公示期
- ✅ 关联相关 Issue (e.g. `Fixes #123`)
- ✅ 包含测试 (新功能必须)
- ✅ 更新文档 (如果是用户可见的变更)
- ✅ 单个 PR 控制在 500 行 diff 内 (超出需要拆分理由)

### Commit Message 规范

参考 [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

- **type**: feat / fix / docs / refactor / test / chore / perf
- **scope**: ingest / storage / evaluator / evolution / api / web / sdk / docs
- **subject**: 50 字以内,动词开头 (e.g. "add", "fix", "refactor")
- **body**: 详细说明动机 + 改动 (可选)
- **footer**: 关联 Issue / 破坏性变更 (可选)

---

## 🌍 翻译文档

- 文档源语言: **中文**
- 次要语言: **English**
- 翻译流程: 修改中文 → 在 `docs/i18n/en/` 创建对应英文版 (Phase 2+)

---

## 🧪 本地开发

### 快速启动 (Docker Compose)

```bash
# 启动 ClickHouse + Kafka (Phase 1 数据层)
docker compose up -d clickhouse kafka

# 等待 30 秒
docker compose ps

# 启动 Ingest Gateway (Phase 1 数据接入)
cd ingest
go run .

# 发送测试 trace
curl -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  -d @testdata/sample-trace.json
```

### 完整开发环境

详见 `docs/development.md` (Phase 2 写)。

---

## 📋 发布流程

| 类型 | 频率 | 触发 |
|---|---|---|
| Patch (Bugfix) | 按需 | 修复关键 Bug |
| Minor (Feature) | 月度 | 每月第一个周二 |
| Major (Breaking) | 季度 | 每季度第一个周二 |
| LTS | 半年 | 维护 12 个月 |

发布由 Maintainer 执行,流程:
1. 更新 `CHANGELOG.md`
2. 打 Git tag (`v0.x.y`)
3. 触发 GitHub Actions 构建 Docker 镜像 + Helm Chart
4. 写 GitHub Release notes
5. 通知社区 (GitHub Discussions + 微信群)

---

## 🛡️ 安全

发现安全漏洞请 **不要** 直接提公开 Issue,发送邮件到 `security@iaoep.io` (待定) 或私下联系 Maintainer。

---

## 💬 社区

- **GitHub Discussions**: 架构讨论 / RFC
- **Issues**: Bug / Feature
- **微信群 / 飞书群**: (筹备中)
- **Discord**: (筹备中)

---

## 📜 License

贡献的代码默认采用 [MIT License](LICENSE)。

---

<p align="center">
  感谢你成为 IAOEP 社区的一员 ❤️
</p>
