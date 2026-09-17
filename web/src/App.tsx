import { Routes, Route, Navigate } from 'react-router-dom';
import { Sidebar } from '@/components/layout/Sidebar';
import { Dashboard } from '@/pages/Dashboard';
import { TraceList } from '@/pages/TraceList';
import { TraceDetail } from '@/pages/TraceDetail';
import { EvaluationList } from '@/pages/EvaluationList';
import { EvaluationDetail } from '@/pages/EvaluationDetail';
import { Regression } from '@/pages/Regression';
import { DatasetList } from '@/pages/DatasetList';
import { Approvals } from '@/pages/Approvals';
import { Evolution } from '@/pages/Evolution';
import { Federation } from '@/pages/Federation';
import { FederationTimeline } from '@/pages/FederationTimeline';
import { FederationAlerts } from '@/pages/FederationAlerts';
import { Settings } from '@/pages/Settings';
import { Compare } from '@/pages/Compare';

export default function App() {
  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <main className="flex-1 overflow-auto">
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/traces" element={<TraceList />} />
          <Route path="/traces/:traceId" element={<TraceDetail />} />
          <Route path="/datasets" element={<DatasetList />} />
          <Route path="/evaluations" element={<EvaluationList />} />
          <Route path="/evaluations/:jobId" element={<EvaluationDetail />} />
          <Route path="/regression" element={<Regression />} />
          <Route path="/approvals" element={<Approvals />} />
          <Route path="/evolution" element={<Evolution />} />
          <Route path="/federation" element={<Federation />} />
          <Route path="/federation/timeline" element={<FederationTimeline />} />
          <Route path="/federation/alerts" element={<FederationAlerts />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="/vs" element={<Compare />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </main>
    </div>
  );
}
