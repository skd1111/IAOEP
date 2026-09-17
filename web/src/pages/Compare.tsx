import { Link } from 'react-router-dom';
import {
  Check, X, Minus, Shield, GitCommit, Globe,
  Zap, Eye, Sparkles,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { cn } from '@/lib/utils';

/**
 * 能力展示页 — 突出 IAOEP 独有亮点
 *
 * 核心叙事: 从观测到自进化，不只是看到问题，而是自动解决问题
 */

type CellStatus = 'yes' | 'no' | 'partial' | 'unique';

interface CompareRow {
  feature: string;
  description?: string;
  langfuse: CellStatus;
  langsmith: CellStatus;
  phoenix: CellStatus;
  braintrust: CellStatus;
  iaoep: CellStatus;
  highlight?: boolean;  // IAOEP 独有功能高亮
}

const sections: { title: string; rows: CompareRow[] }[] = [
  {
    title: '基础可观测性',
    rows: [
      { feature: '分布式 Trace', langfuse: 'yes', langsmith: 'yes', phoenix: 'yes', braintrust: 'yes', iaoep: 'yes' },
      { feature: 'Waterfall 时间轴', langfuse: 'yes', langsmith: 'yes', phoenix: 'yes', braintrust: 'yes', iaoep: 'yes' },
      { feature: 'LLM 输入/输出展示', langfuse: 'yes', langsmith: 'yes', phoenix: 'partial', braintrust: 'yes', iaoep: 'yes' },
      { feature: '成本追踪', langfuse: 'yes', langsmith: 'yes', phoenix: 'partial', braintrust: 'yes', iaoep: 'yes' },
      { feature: 'OTLP 原生协议', langfuse: 'partial', langsmith: 'partial', phoenix: 'yes', braintrust: 'partial', iaoep: 'unique' },
    ],
  },
  {
    title: '评测与质量',
    rows: [
      { feature: 'Golden Dataset', langfuse: 'yes', langsmith: 'yes', phoenix: 'yes', braintrust: 'yes', iaoep: 'yes' },
      { feature: 'LLM-as-Judge', langfuse: 'yes', langsmith: 'yes', phoenix: 'yes', braintrust: 'yes', iaoep: 'yes' },
      { feature: 'Dataset Run 对比 UI', langfuse: 'no', langsmith: 'partial', phoenix: 'no', braintrust: 'partial', iaoep: 'yes', highlight: true },
      { feature: '回归检测', langfuse: 'partial', langsmith: 'yes', phoenix: 'no', braintrust: 'yes', iaoep: 'yes' },
    ],
  },
  {
    title: '自进化能力 (IAOEP 独有)',
    rows: [
      { feature: 'Analyst Agent 自动分析', langfuse: 'no', langsmith: 'no', phoenix: 'no', braintrust: 'no', iaoep: 'unique', highlight: true },
      { feature: 'A/B 测试框架', langfuse: 'no', langsmith: 'no', phoenix: 'no', braintrust: 'partial', iaoep: 'unique', highlight: true },
      { feature: '三级审批流 (Low/Med/High)', langfuse: 'no', langsmith: 'no', phoenix: 'no', braintrust: 'no', iaoep: 'unique', highlight: true },
      { feature: 'SOP 双签 + SLA', langfuse: 'no', langsmith: 'no', phoenix: 'no', braintrust: 'no', iaoep: 'unique', highlight: true },
      { feature: 'Decision Agent 自动回滚', langfuse: 'no', langsmith: 'no', phoenix: 'no', braintrust: 'no', iaoep: 'unique', highlight: true },
      { feature: '演进历史时间线', langfuse: 'no', langsmith: 'no', phoenix: 'no', braintrust: 'no', iaoep: 'unique', highlight: true },
    ],
  },
  {
    title: '企业级能力',
    rows: [
      { feature: '多租户隔离', langfuse: 'partial', langsmith: 'partial', phoenix: 'partial', braintrust: 'partial', iaoep: 'yes' },
      { feature: '联邦学习 + 差分隐私', langfuse: 'no', langsmith: 'no', phoenix: 'no', braintrust: 'no', iaoep: 'unique', highlight: true },
      { feature: '跨租户聚合分析', langfuse: 'no', langsmith: 'no', phoenix: 'no', braintrust: 'no', iaoep: 'unique', highlight: true },
      { feature: 'GDPR 合规', langfuse: 'partial', langsmith: 'yes', phoenix: 'partial', braintrust: 'partial', iaoep: 'yes' },
    ],
  },
  {
    title: 'SDK 与接入',
    rows: [
      { feature: 'Python SDK', langfuse: 'yes', langsmith: 'yes', phoenix: 'yes', braintrust: 'yes', iaoep: 'yes' },
      { feature: 'TypeScript SDK', langfuse: 'yes', langsmith: 'yes', phoenix: 'yes', braintrust: 'yes', iaoep: 'yes' },
      { feature: 'Java SDK (原生注解)', langfuse: 'no', langsmith: 'no', phoenix: 'no', braintrust: 'no', iaoep: 'unique', highlight: true },
      { feature: 'Go SDK (原生装饰器)', langfuse: 'no', langsmith: 'no', phoenix: 'no', braintrust: 'no', iaoep: 'unique', highlight: true },
      { feature: '零 SDK 直接 OTLP', langfuse: 'partial', langsmith: 'no', phoenix: 'yes', braintrust: 'no', iaoep: 'unique' },
      { feature: 'LangChain 自动 Patch', langfuse: 'yes', langsmith: 'yes', phoenix: 'partial', braintrust: 'partial', iaoep: 'yes' },
    ],
  },
  {
    title: '开源与成本',
    rows: [
      { feature: '完全开源 (MIT)', langfuse: 'yes', langsmith: 'no', phoenix: 'no', braintrust: 'no', iaoep: 'yes' },
      { feature: '免费自托管', langfuse: 'yes', langsmith: 'no', phoenix: 'yes', braintrust: 'no', iaoep: 'yes' },
      { feature: '20 万 traces/月成本', langfuse: 'yes', langsmith: 'no', phoenix: 'yes', braintrust: 'no', iaoep: 'yes' },
    ],
  },
];

const statusIcon: Record<CellStatus, { icon: typeof Check; color: string }> = {
  yes:     { icon: Check,   color: 'text-green-500' },
  no:      { icon: X,       color: 'text-red-400' },
  partial: { icon: Minus,   color: 'text-yellow-500' },
  unique:  { icon: Sparkles, color: 'text-purple-500' },
};

export function Compare() {
  return (
    <div className="p-6 space-y-6 max-w-6xl mx-auto">
      {/* Hero */}
      <div className="text-center space-y-3 py-6">
        <h1 className="text-3xl font-bold">
          为什么选择 IAOEP?
        </h1>
        <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
          从<span className="font-semibold text-foreground">「看到问题」</span>到
          <span className="font-semibold text-purple-500">「自动解决问题」</span>
        </p>
        <p className="text-sm text-muted-foreground">
          唯一实现 观测 → 评测 → 自进化 完整闭环的开源平台
        </p>
      </div>

      {/* 三大独有亮点 */}
      <div className="grid grid-cols-3 gap-4">
        <HighlightCard
          icon={GitCommit}
          title="自进化闭环"
          desc="不只是看到问题，而是自动发现问题 → 分析 → 审批 → 应用 → 回滚，形成完整闭环。"
          color="purple"
        />
        <HighlightCard
          icon={Shield}
          title="联邦学习 + 差分隐私"
          desc="跨租户聚合分析不泄露明文，满足 GDPR 合规。SaaS 平台型公司的刚需。"
          color="cyan"
        />
        <HighlightCard
          icon={Globe}
          title="真·多语言 SDK"
          desc="Java/Go 不只是 OTel 透传，而是原生注解/装饰器 + 完整 SDK 级支持（prompt、scoring、A/B）。"
          color="green"
        />
      </div>

      {/* 对比表格 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-center text-lg">功能对比矩阵</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/30">
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground w-[200px]">功能</th>
                  <th className="text-center px-3 py-3 font-medium text-muted-foreground">Langfuse</th>
                  <th className="text-center px-3 py-3 font-medium text-muted-foreground">LangSmith</th>
                  <th className="text-center px-3 py-3 font-medium text-muted-foreground">Phoenix</th>
                  <th className="text-center px-3 py-3 font-medium text-muted-foreground">Braintrust</th>
                  <th className="text-center px-3 py-3 font-semibold text-purple-500 bg-purple-500/5">
                    IAOEP
                  </th>
                </tr>
              </thead>
              <tbody>
                {sections.map((section) => (
                  <>
                    <tr key={`sec-${section.title}`}>
                      <td
                        colSpan={6}
                        className="px-4 py-2 text-xs font-semibold text-muted-foreground uppercase tracking-wider bg-muted/20 border-b border-border"
                      >
                        {section.title}
                      </td>
                    </tr>
                    {section.rows.map((row) => (
                      <tr
                        key={row.feature}
                        className={cn(
                          'border-b border-border/50 hover:bg-muted/10 transition-colors',
                          row.highlight && 'bg-purple-500/[0.03]'
                        )}
                      >
                        <td className="px-4 py-2.5">
                          <div className="flex items-center gap-2">
                            {row.highlight && <Sparkles className="w-3 h-3 text-purple-500 shrink-0" />}
                            <span className={row.highlight ? 'font-medium' : ''}>{row.feature}</span>
                          </div>
                        </td>
                        <Cell status={row.langfuse} />
                        <Cell status={row.langsmith} />
                        <Cell status={row.phoenix} />
                        <Cell status={row.braintrust} />
                        <td className="text-center px-3 py-2.5 bg-purple-500/5">
                          <CellIcon status={row.iaoep} />
                        </td>
                      </tr>
                    ))}
                  </>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* 图例 */}
      <div className="flex items-center justify-center gap-6 text-xs text-muted-foreground">
        <span className="flex items-center gap-1"><Check className="w-3 h-3 text-green-500" /> 完整支持</span>
        <span className="flex items-center gap-1"><Minus className="w-3 h-3 text-yellow-500" /> 部分支持</span>
        <span className="flex items-center gap-1"><X className="w-3 h-3 text-red-400" /> 不支持</span>
        <span className="flex items-center gap-1"><Sparkles className="w-3 h-3 text-purple-500" /> 独家功能</span>
      </div>

      {/* CTA */}
      <Card className="border-purple-500/20 bg-gradient-to-r from-purple-500/5 via-transparent to-cyan-500/5">
        <CardContent className="p-8 text-center space-y-4">
          <h2 className="text-xl font-bold">准备好体验自进化了吗?</h2>
          <p className="text-sm text-muted-foreground max-w-lg mx-auto">
            5 分钟接入，零 SDK 依赖。任何语言、任何 Agent 框架，只要能发 OTLP 就能接入。
          </p>
          <div className="flex items-center justify-center gap-3">
            <Link
              to="/dashboard"
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-primary text-primary-foreground rounded-md text-sm font-medium hover:bg-primary/90"
            >
              <Eye className="w-4 h-4" />
              查看 Demo
            </Link>
            <Link
              to="/traces"
              className="inline-flex items-center gap-2 px-5 py-2.5 border border-border rounded-md text-sm font-medium hover:bg-accent"
            >
              <Zap className="w-4 h-4" />
              浏览 Traces
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function Cell({ status }: { status: CellStatus }) {
  return (
    <td className="text-center px-3 py-2.5">
      <CellIcon status={status} />
    </td>
  );
}

function CellIcon({ status }: { status: CellStatus }) {
  const config = statusIcon[status];
  const Icon = config.icon;
  return <Icon className={cn('w-4 h-4 inline-block', config.color)} />;
}

function HighlightCard({ icon: Icon, title, desc, color }: {
  icon: typeof Check;
  title: string;
  desc: string;
  color: 'purple' | 'cyan' | 'green';
}) {
  const colors = {
    purple: { bg: 'bg-purple-500/10', text: 'text-purple-500', border: 'border-purple-500/20' },
    cyan:   { bg: 'bg-cyan-500/10',   text: 'text-cyan-500',   border: 'border-cyan-500/20' },
    green:  { bg: 'bg-green-500/10',  text: 'text-green-500',  border: 'border-green-500/20' },
  }[color];

  return (
    <Card className={cn('hover:shadow-md transition-shadow', colors.border)}>
      <CardContent className="p-5 space-y-3">
        <div className={cn('p-2.5 rounded-lg w-fit', colors.bg)}>
          <Icon className={cn('w-5 h-5', colors.text)} />
        </div>
        <h3 className="font-semibold">{title}</h3>
        <p className="text-sm text-muted-foreground leading-relaxed">{desc}</p>
      </CardContent>
    </Card>
  );
}
