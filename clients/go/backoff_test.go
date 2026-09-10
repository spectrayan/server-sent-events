package sseclient

import (
	"testing"
	"time"
)

func TestVersion(t *testing.T) {
	if Version != "2.0.1" {
		t.Errorf("expected Version '2.0.1', got '%s'", Version)
	}
}

func TestBackoff_Defaults(t *testing.T) {
	cfg := DefaultReconnectionConfig()
	if cfg.InitialDelay != 1*time.Second {
		t.Errorf("expected 1s, got %v", cfg.InitialDelay)
	}
	if cfg.MaxDelay != 30*time.Second {
		t.Errorf("expected 30s, got %v", cfg.MaxDelay)
	}
	if cfg.Multiplier != 2.0 {
		t.Errorf("expected 2.0, got %v", cfg.Multiplier)
	}
	if cfg.Jitter != 0.2 {
		t.Errorf("expected 0.2, got %v", cfg.Jitter)
	}
	if cfg.MaxRetries != 0 {
		t.Errorf("expected 0, got %v", cfg.MaxRetries)
	}
}

func TestBackoff_ExponentialGrowthWithoutJitter(t *testing.T) {
	cfg := ReconnectionConfig{
		InitialDelay: 100 * time.Millisecond,
		MaxDelay:     10 * time.Second,
		Multiplier:   2.0,
		Jitter:       0.0,
	}
	b := NewBackoff(cfg)

	// Attempt 0 -> initial
	if d := b.Delay(0); d != 100*time.Millisecond {
		t.Errorf("attempt 0: expected 100ms, got %v", d)
	}
	// Attempt 1 -> 100ms * 2 = 200ms
	if d := b.Delay(1); d != 200*time.Millisecond {
		t.Errorf("attempt 1: expected 200ms, got %v", d)
	}
	// Attempt 2 -> 100ms * 4 = 400ms
	if d := b.Delay(2); d != 400*time.Millisecond {
		t.Errorf("attempt 2: expected 400ms, got %v", d)
	}
	// Attempt 3 -> 100ms * 8 = 800ms
	if d := b.Delay(3); d != 800*time.Millisecond {
		t.Errorf("attempt 3: expected 800ms, got %v", d)
	}
}

func TestBackoff_MaxDelayCap(t *testing.T) {
	cfg := ReconnectionConfig{
		InitialDelay: 1 * time.Second,
		MaxDelay:     5 * time.Second,
		Multiplier:   2.0,
		Jitter:       0.0,
	}
	b := NewBackoff(cfg)

	// Attempt 10 would be 1024s without cap, must be capped at 5s
	if d := b.Delay(10); d != 5*time.Second {
		t.Errorf("expected capped delay 5s, got %v", d)
	}
}

func TestBackoff_JitterBounds(t *testing.T) {
	cfg := ReconnectionConfig{
		InitialDelay: 1 * time.Second,
		MaxDelay:     30 * time.Second,
		Multiplier:   2.0,
		Jitter:       0.2, // ±20%
	}
	b := NewBackoff(cfg)

	for attempt := 0; attempt < 5; attempt++ {
		for run := 0; run < 20; run++ {
			d := b.Delay(attempt)
			base := float64(1*time.Second) * float64(int(1)<<attempt)
			if base > float64(30*time.Second) {
				base = float64(30 * time.Second)
			}
			minAllowed := time.Duration(base * 0.79) // allow small float rounding
			maxAllowed := time.Duration(base * 1.21)

			if d < minAllowed || d > maxAllowed {
				t.Fatalf("attempt %d, run %d: delay %v out of bounds [%v, %v]", attempt, run, d, minAllowed, maxAllowed)
			}
		}
	}
}
