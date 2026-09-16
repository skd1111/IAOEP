// Package config 负责 Ingest Gateway 的配置加载.
//
// 配置通过环境变量注入 (12-factor):
//   - IAOEP_INGEST_HTTP_PORT     HTTP 监听端口 (默认 4318)
//   - IAOEP_INGEST_GRPC_PORT     gRPC 监听端口 (默认 4317)
//   - IAOEP_INGEST_KAFKA_BROKERS Kafka broker 地址 (默认 localhost:29092)
//   - IAOEP_INGEST_KAFKA_TOPIC   Kafka topic (默认 iaoep.traces)
//   - IAOEP_INGEST_API_KEYS      多租户 API Key (格式 "tenant1:key1,tenant2:key2")
//   - IAOEP_INGEST_RATE_LIMIT    每 tenant 速率 (默认 100 spans/s)
//   - IAOEP_INGEST_RATE_BURST    每 tenant 突发 (默认 1000 spans)
package config

import (
	"os"
	"strconv"
)

type Config struct {
	HTTPPort      int
	GRPCPort      int
	KafkaBrokers  string
	KafkaTopic    string
	APIKeys       string // 格式 "tenant1:key1,tenant2:key2"
	RateLimit     float64
	RateBurst     float64
	DefaultTenant string
}

func Load() *Config {
	return &Config{
		HTTPPort:      envInt("IAOEP_INGEST_HTTP_PORT", 4318),
		GRPCPort:      envInt("IAOEP_INGEST_GRPC_PORT", 4317),
		KafkaBrokers:  envStr("IAOEP_INGEST_KAFKA_BROKERS", "localhost:29092"),
		KafkaTopic:    envStr("IAOEP_INGEST_KAFKA_TOPIC", "iaoep.traces"),
		APIKeys:       envStr("IAOEP_INGEST_API_KEYS", ""),
		RateLimit:     envFloat("IAOEP_INGEST_RATE_LIMIT", 100),
		RateBurst:     envFloat("IAOEP_INGEST_RATE_BURST", 1000),
		DefaultTenant: envStr("IAOEP_INGEST_DEFAULT_TENANT", "default"),
	}
}

func (c *Config) HTTPAddr() string {
	return ":" + strconv.Itoa(c.HTTPPort)
}

func (c *Config) GRPCAddr() string {
	return ":" + strconv.Itoa(c.GRPCPort)
}

func envStr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func envInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			return i
		}
	}
	return def
}

func envFloat(key string, def float64) float64 {
	if v := os.Getenv(key); v != "" {
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			return f
		}
	}
	return def
}
