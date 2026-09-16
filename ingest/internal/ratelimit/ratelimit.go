// Package ratelimit 实现基于 tenant 的令牌桶限流.
//
// 算法: 经典令牌桶 (token bucket)
//   - 每个 tenant 有独立桶
//   - 桶容量 = 突发上限 (e.g. 1000 spans / 秒)
//   - 补充速率 = 持续速率 (e.g. 100 spans / 秒)
//   - 桶空时拒绝 (返回 429)
//
// Phase 1 简化: 使用 sync.Map 存内存中的桶. Phase 2 可换 Redis.
package ratelimit

import (
	"sync"
	"time"
)

// Limiter 限流器 (按 tenant 维度)
type Limiter struct {
	mu       sync.Mutex
	buckets  map[string]*bucket
	rate     float64       // tokens per second
	capacity float64       // max bucket size
}

type bucket struct {
	tokens     float64
	lastRefill time.Time
}

// NewLimiter 创建限流器.
//   rate: 持续速率 (e.g. 100 = 100 spans / 秒)
//   capacity: 桶容量 (e.g. 1000 = 突发可至 1000 spans)
func NewLimiter(rate, capacity float64) *Limiter {
	return &Limiter{
		buckets:  make(map[string]*bucket),
		rate:     rate,
		capacity: capacity,
	}
}

// Allow 检查 tenant 是否允许通过 (消费一个 token), 返回是否允许.
func (l *Limiter) Allow(tenantID string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()

	b, ok := l.buckets[tenantID]
	now := time.Now()
	if !ok {
		// 新 tenant, 桶满
		l.buckets[tenantID] = &bucket{tokens: l.capacity - 1, lastRefill: now}
		return true
	}

	// 补充 token
	elapsed := now.Sub(b.lastRefill).Seconds()
	b.tokens += elapsed * l.rate
	if b.tokens > l.capacity {
		b.tokens = l.capacity
	}
	b.lastRefill = now

	if b.tokens < 1 {
		return false
	}
	b.tokens--
	return true
}

// Limit 装饰 HTTP handler, 鉴权后做限流.
func (l *Limiter) Limit(tenantID string, onAllow func()) (allowed bool) {
	if l.Allow(tenantID) {
		onAllow()
		return true
	}
	return false
}
