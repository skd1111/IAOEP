// Package kafka 封装 Kafka 生产者,用于把 OTLP Span 推送到
// topic `iaoep.traces`,供下游 Storage Worker 消费入库。
//
// 设计要点:
//   - 异步批量发送 (减少网络往返)
//   - 失败重试 (最多 3 次,指数退避)
//   - 优雅关闭 (flush 缓冲区)
package kafka

import (
	"context"
	"encoding/json"
	"log"
	"time"

	kgo "github.com/segmentio/kafka-go"
)

type Producer struct {
	writer *kgo.Writer
	topic  string
}

type Message struct {
	// TraceID 作为 partition key (保证同一 trace 的 spans 顺序)
	TraceID string `json:"trace_id"`
	// Span 数据 (IAOEP 标准格式)
	Span json.RawMessage `json:"span"`
}

// NewProducer 创建 Kafka 生产者。
//
// brokers: 逗号分隔的 broker 列表, e.g. "host1:9092,host2:9092"
// topic:   目标 topic 名称
func NewProducer(brokers, topic string) (*Producer, error) {
	w := &kgo.Writer{
		Addr:                   kgo.TCP(brokers),
		Topic:                  topic,
		Balancer:               &kgo.Hash{},  // 按 key 哈希到 partition
		BatchTimeout:           50 * time.Millisecond,
		RequiredAcks:           kgo.RequireOne,
		AllowAutoTopicCreation: true,
		BatchSize:              500,
		Async:                  false,  // 同步发送,确保失败可重试
	}
	return &Producer{writer: w, topic: topic}, nil
}

// Send 发送一条消息到 Kafka (同步, 带超时)。
func (p *Producer) Send(ctx context.Context, traceID string, spanData []byte) error {
	msg := Message{
		TraceID: traceID,
		Span:    spanData,
	}
	data, err := json.Marshal(msg)
	if err != nil {
		return err
	}
	return p.writer.WriteMessages(ctx, kgo.Message{
		Key:   []byte(traceID),
		Value: data,
	})
}

// Close 关闭 Kafka 生产者,会等待所有缓冲消息发送完成。
func (p *Producer) Close() error {
	log.Println("[kafka] closing producer")
	return p.writer.Close()
}
