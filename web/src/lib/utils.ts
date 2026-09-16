import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * shadcn/ui 推荐的 cn() helper: 合并 className + 处理冲突.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * 格式化时间为相对时间 (e.g. "5s ago", "2m ago")
 */
export function formatRelativeTime(epochMs: number, now: number = Date.now()): string {
  const diffMs = now - epochMs;
  if (diffMs < 1000) return '刚刚';
  const sec = Math.floor(diffMs / 1000);
  if (sec < 60) return `${sec}s ago`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}h ago`;
  const day = Math.floor(hr / 24);
  return `${day}d ago`;
}

/**
 * 格式化 token 数 (大数加千分位)
 */
export function formatNumber(n: number): string {
  if (n < 1000) return n.toString();
  if (n < 1_000_000) return `${(n / 1000).toFixed(1)}k`;
  return `${(n / 1_000_000).toFixed(2)}M`;
}

/**
 * 格式化成本 (CNY)
 */
export function formatCost(cny: number): string {
  if (cny < 0.01) return `¥${cny.toFixed(4)}`;
  return `¥${cny.toFixed(2)}`;
}

/**
 * 格式化耗时 (ms)
 */
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}
