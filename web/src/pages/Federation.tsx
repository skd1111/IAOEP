import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Shield, Eye, EyeOff, Plus } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi, type MockFederationAggregate } from '@/lib/api';
import { cn, formatRelativeTime } from '@/lib/utils';

/**
 * Federation Page — 跨租户聚合统计 + DP 噪声可视化.
 */
export function Federation() {
  const qc = useQueryClient();
  const [showTruth, setShowTruth] = useState(false);   // Owner 视角开关
  const [showCreate, setShowCreate] = useState(false);

  const { data } = useQuery({
    queryKey: ['federation'],
    queryFn: mockApi.federation,
    refetchInterval: 10_000,
  });

  const aggregates: MockFederationAggregate[] = data ?? [];

  const trigger = useMutation({
    mutationFn: mockApi.triggerFederation,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['federation'] }),
  });

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold flex items-center gap-2">
            <Shield className="w-6 h-6" />
            Federation
            <span className="text-xs px-2 py-0.5 rounded-full bg-primary/10 text-primary ml-2">
              ε-DP
            </span>
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            跨租户聚合统计 · Laplace noise · 租户 ID SHA-256 hash
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowTruth(!showTruth)}
            className={cn(
              'flex items-center gap-2 px-3 py-1.5 rounded-md text-sm border',
              showTruth
                ? 'bg-orange-500/10 border-orange-500 text-orange-500'
                : 'border-border text-muted-foreground hover:bg-accent'
            )}
            title="切换 true/noisy 视角 (Owner 模式可看真实值)"
          >
            {showTruth ? <Eye className="w-4 h-4" /> : <EyeOff className="w-4 h-4" />}
            {showTruth ? 'Owner 模式 (真值)' : '公开模式 (噪声)'}
          </button>
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md"
          >
            <Plus className="w-4 h-4" />
            触发聚合
          </button>
        </div>
      </div>

      {showCreate && (
        <Card>
          <CardHeader>
            <CardTitle>触发跨租户聚合</CardTitle>
          </CardHeader>
          <CardContent>
            <CreateFederationForm
              onSubmit={(req) => {
                trigger.mutate(req);
                setShowCreate(false);
              }}
            />
          </CardContent>
        </Card>
      )}

      {/* 聚合列表 */}
      <div className="grid grid-cols-2 gap-4">
        {aggregates.map((agg) => (
          <AggregateCard key={agg.id} aggregate={agg} showTruth={showTruth} />
        ))}
      </div>

      {aggregates.length === 0 && (
        <Card>
          <CardContent className="p-12 text-center text-muted-foreground">
            暂无跨租户聚合, 点击右上角"触发聚合"开始
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function AggregateCard({
  aggregate,
  showTruth,
}: {
  aggregate: MockFederationAggregate;
  showTruth: boolean;
}) {
  const diff = showTruth ? (aggregate.trueValue ?? 0) - aggregate.noisyValue : 0;
  const diffPercent = aggregate.trueValue !== undefined && aggregate.trueValue !== 0
    ? Math.abs(diff / aggregate.trueValue) * 100
    : 0;

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center justify-between text-base">
          <span>{aggregate.name}</span>
          <span className="text-xs px-2 py-0.5 rounded-full bg-secondary font-mono">
            {aggregate.dimension}
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex items-end gap-2">
          <div className="text-4xl font-bold">
            {aggregate.noisyValue.toFixed(3)}
          </div>
          <div className="text-sm text-muted-foreground mb-1">
            ± noise (ε = {aggregate.epsilon})
          </div>
        </div>

        {showTruth && aggregate.trueValue !== undefined && (
          <div className="p-2 rounded-md bg-orange-500/10 border border-orange-500/20">
            <div className="text-xs text-orange-500 font-medium">Owner 视角</div>
            <div className="flex items-baseline gap-2 mt-1">
              <span className="text-2xl font-mono">{aggregate.trueValue.toFixed(3)}</span>
              <span className="text-xs text-muted-foreground">
                (噪声偏移: {diff.toFixed(3)} ≈ {diffPercent.toFixed(1)}%)
              </span>
            </div>
          </div>
        )}

        <div className="grid grid-cols-3 gap-3 pt-2 border-t border-border text-xs">
          <div>
            <div className="text-muted-foreground">样本数</div>
            <div className="font-mono text-base">{aggregate.sampleSize}</div>
          </div>
          <div>
            <div className="text-muted-foreground">敏感度</div>
            <div className="font-mono text-base">{aggregate.sensitivity.toFixed(3)}</div>
          </div>
          <div>
            <div className="text-muted-foreground">创建</div>
            <div className="font-mono text-base">
              {formatRelativeTime(new Date(aggregate.createdAt).getTime())}
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function CreateFederationForm({
  onSubmit,
}: {
  onSubmit: (req: {
    name: string;
    dimension: string;
    tenantValues: Record<string, number>;
    epsilon: number;
  }) => void;
}) {
  const [name, setName] = useState('model_accuracy');
  const [dimension, setDimension] = useState('model=qwen3-turbo');
  const [tenantValuesStr, setTenantValuesStr] = useState(
    'tenantA:0.92,tenantB:0.85,tenantC:0.91',
  );
  const [epsilon, setEpsilon] = useState(1.0);

  const submit = () => {
    const tenantValues: Record<string, number> = {};
    tenantValuesStr.split(',').forEach((kv) => {
      const [k, v] = kv.split(':').map((s) => s.trim());
      if (k && v) tenantValues[k] = parseFloat(v);
    });
    onSubmit({ name, dimension, tenantValues, epsilon });
  };

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="text-xs text-muted-foreground">指标名</label>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm mt-1"
          />
        </div>
        <div>
          <label className="text-xs text-muted-foreground">维度</label>
          <input
            value={dimension}
            onChange={(e) => setDimension(e.target.value)}
            className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm mt-1"
          />
        </div>
      </div>
      <div>
        <label className="text-xs text-muted-foreground">
          各租户指标 (格式: tenant:value,tenant:value,...)
        </label>
        <input
          value={tenantValuesStr}
          onChange={(e) => setTenantValuesStr(e.target.value)}
          className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm mt-1 font-mono"
          placeholder="tenantA:0.92,tenantB:0.85,..."
        />
      </div>
      <div className="flex items-center gap-3">
        <div className="flex-1">
          <label className="text-xs text-muted-foreground">
            ε (隐私预算, 默认 1.0)
          </label>
          <input
            type="number"
            step="0.1"
            value={epsilon}
            onChange={(e) => setEpsilon(parseFloat(e.target.value))}
            className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm mt-1"
          />
        </div>
        <button
          onClick={submit}
          className="px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm self-end"
        >
          提交
        </button>
      </div>
    </div>
  );
}
