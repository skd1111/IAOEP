import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Plus, Database } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi, type MockDataset } from '@/lib/api';
import { formatRelativeTime } from '@/lib/utils';

/**
 * Golden Dataset 管理页 — Phase 2
 */
export function DatasetList() {
  const [showCreate, setShowCreate] = useState(false);

  const { data } = useQuery({
    queryKey: ['datasets'],
    queryFn: mockApi.datasets,
  });

  const datasets: MockDataset[] = data ?? [];

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Golden Datasets</h1>
          <p className="text-sm text-muted-foreground mt-1">
            评测数据集, 按 (name, version) 多版本管理
          </p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md"
        >
          <Plus className="w-4 h-4" />
          新建
        </button>
      </div>

      {showCreate && (
        <Card>
          <CardHeader>
            <CardTitle>新建 Dataset</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-3 text-sm text-muted-foreground">
              <p>Phase 2 占位 — 完整创建表单在 PR 17 后续迭代中加入.</p>
              <p>当前用 mock 数据演示, backend API 接通后切换到真实数据.</p>
            </div>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardContent className="p-0">
          <div className="divide-y divide-border">
            {datasets.map((d) => (
              <div
                key={d.id}
                className="flex items-center gap-4 p-4 hover:bg-accent transition-colors"
              >
                <Database className="w-5 h-5 text-muted-foreground shrink-0" />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium">{d.name}</span>
                    <span className="font-mono text-xs px-2 py-0.5 rounded-full bg-secondary">
                      {d.version}
                    </span>
                    {d.isActive && (
                      <span className="text-xs px-2 py-0.5 rounded-full bg-green-500/10 text-green-500">
                        active
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-muted-foreground mt-1">
                    {d.caseCount} cases · {formatRelativeTime(d.updatedAt)}
                  </div>
                </div>
                <Link to={`/datasets/${d.id}`} className="text-xs text-primary hover:underline">
                  详情
                </Link>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
