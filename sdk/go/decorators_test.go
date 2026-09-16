package iaoep

import (
	"context"
	"errors"
	"testing"
)

func TestObservedAgent_Success(t *testing.T) {
	_, err := ObservedAgent(context.Background(), "test-agent", "general",
		func(ctx context.Context) (string, error) {
			return "ok", nil
		})
	if err != nil {
		t.Fatalf("expected nil, got %v", err)
	}
}

func TestObservedAgent_Error(t *testing.T) {
	_, err := ObservedAgent(context.Background(), "test-agent", "general",
		func(ctx context.Context) (string, error) {
			return "", errors.New("simulated error")
		})
	if err == nil {
		t.Fatalf("expected error")
	}
}

func TestObservedLLMCall(t *testing.T) {
	_, err := ObservedLLMCall(context.Background(), "openai", "qwen3-turbo", 100, 50,
		func(ctx context.Context) (string, error) {
			return "response", nil
		})
	if err != nil {
		t.Fatalf("expected nil, got %v", err)
	}
}

func TestObservedTool(t *testing.T) {
	_, err := ObservedTool(context.Background(), "get_current_time",
		func(ctx context.Context) (string, error) {
			return "2027-09-01T00:00:00Z", nil
		})
	if err != nil {
		t.Fatalf("expected nil, got %v", err)
	}
}

func TestObservedABTest_GroupSet(t *testing.T) {
	called := false
	_, _ = ObservedABTest(context.Background(), "test-ab",
		func(ctx context.Context) (string, error) {
			group := ABTestGroupFromContext(ctx)
			if group != "baseline" && group != "candidate" {
				t.Errorf("unexpected group: %s", group)
			}
			called = true
			return "", nil
		})
	if !called {
		t.Errorf("function not invoked")
	}
}
