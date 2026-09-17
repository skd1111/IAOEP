// Package main 是 IAOEP Ingest Gateway 的入口.
//
// Ingest Gateway 接收 OTLP (gRPC + HTTP) 数据,解析 Span 树,
// 映射为 IAOEP 标准字段,发送到 Kafka topic `iaoep.traces`.
//
// 参考: docs/agentops-platform-design.md § 4 系统架构 / § 5.1 OTLP 接入流程
package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/iaoep/ingest/internal/auth"
	"github.com/iaoep/ingest/internal/config"
	"github.com/iaoep/ingest/internal/kafka"
	"github.com/iaoep/ingest/internal/otlp"
	"github.com/iaoep/ingest/internal/ratelimit"
)

func main() {
	cfg := config.Load()

	log.Printf("[iaoep-ingest] starting: http=:%d grpc=:%d kafka=%s topic=%s rate=%.0f/s burst=%.0f",
		cfg.HTTPPort, cfg.GRPCPort, cfg.KafkaBrokers, cfg.KafkaTopic, cfg.RateLimit, cfg.RateBurst)

	// Kafka 生产者
	producer, err := kafka.NewProducer(cfg.KafkaBrokers, cfg.KafkaTopic)
	if err != nil {
		log.Fatalf("failed to create kafka producer: %v", err)
	}
	defer producer.Close()

	// 多租户鉴权 + 限流
	registry := auth.NewTenantRegistry()
	limiter := ratelimit.NewLimiter(cfg.RateLimit, cfg.RateBurst)

	// OTLP Receiver (注入 registry + limiter)
	receiver := otlp.NewReceiver(producer, registry, limiter, cfg.DefaultTenant)

	// HTTP server (OTLP/HTTP)
	mux := http.NewServeMux()
	mux.HandleFunc("/v1/traces", receiver.HandleHTTPTraces)
	mux.HandleFunc("/v1/metrics", receiver.HandleHTTPMetrics)
	mux.HandleFunc("/v1/logs", receiver.HandleHTTPLogs)
	mux.HandleFunc("/health", receiver.HandleHealth)

	httpServer := &http.Server{
		Addr:              cfg.HTTPAddr(),
		Handler:           http.MaxBytesHandler(mux, 50<<20), // 50 MB
		ReadHeaderTimeout: 10 * time.Second,
	}

	// 启动 HTTP server
	go func() {
		log.Printf("[iaoep-ingest] HTTP server listening on %s", cfg.HTTPAddr())
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("HTTP server error: %v", err)
		}
	}()

	// 启动 gRPC server (OTLP/gRPC)
	go func() {
		log.Printf("[iaoep-ingest] gRPC server listening on %s", cfg.GRPCAddr())
		if err := otlp.RunGRPCServer(cfg.GRPCAddr(), receiver); err != nil {
			log.Printf("gRPC server error: %v", err)
		}
	}()

	// 等待终止信号
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("[iaoep-ingest] shutting down...")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := httpServer.Shutdown(ctx); err != nil {
		log.Printf("HTTP shutdown error: %v", err)
	}
	log.Println("[iaoep-ingest] bye")
}
