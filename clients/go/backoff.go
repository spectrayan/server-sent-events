package sseclient

import (
	"math"
	"math/rand"
	"sync"
	"time"
)

// ReconnectionConfig defines parameters for exponential backoff and automatic reconnection.
type ReconnectionConfig struct {
	// InitialDelay is the delay before the first reconnection attempt. Default is 1 second.
	InitialDelay time.Duration

	// MaxDelay is the maximum backoff delay cap. Default is 30 seconds.
	MaxDelay time.Duration

	// Multiplier is the exponential growth factor. Default is 2.0.
	Multiplier float64

	// Jitter is the randomization factor applied to the computed delay (e.g., 0.2 for ±20%). Default is 0.2.
	Jitter float64

	// MaxRetries is the maximum consecutive retry attempts allowed before giving up. 0 means unlimited. Default is 0.
	MaxRetries int
}

// DefaultReconnectionConfig returns the default reconnection parameters matching Spectrayan standards.
func DefaultReconnectionConfig() ReconnectionConfig {
	return ReconnectionConfig{
		InitialDelay: 1 * time.Second,
		MaxDelay:     30 * time.Second,
		Multiplier:   2.0,
		Jitter:       0.2,
		MaxRetries:   0,
	}
}

// Backoff computes exponential backoff delays with jitter.
type Backoff struct {
	config ReconnectionConfig
	rng    *rand.Rand
	mu     sync.Mutex
}

// NewBackoff constructs a Backoff calculator with the provided configuration.
func NewBackoff(config ReconnectionConfig) *Backoff {
	if config.InitialDelay <= 0 {
		config.InitialDelay = 1 * time.Second
	}
	if config.MaxDelay < config.InitialDelay {
		config.MaxDelay = config.InitialDelay
	}
	if config.Multiplier < 1.0 {
		config.Multiplier = 2.0
	}
	if config.Jitter < 0.0 || config.Jitter > 1.0 {
		config.Jitter = 0.2
	}

	return &Backoff{
		config: config,
		rng:    rand.New(rand.NewSource(time.Now().UnixNano())),
	}
}

// Delay computes the backoff duration for a given 0-indexed attempt count.
// Formula: delay = min(initial * multiplier^attempt, max) * (1 + random(-jitter, +jitter))
func (b *Backoff) Delay(attempt int) time.Duration {
	if attempt <= 0 {
		return b.applyJitter(b.config.InitialDelay)
	}

	multiplierPow := math.Pow(b.config.Multiplier, float64(attempt))
	baseDelaySec := b.config.InitialDelay.Seconds() * multiplierPow
	maxDelaySec := b.config.MaxDelay.Seconds()

	delaySec := math.Min(baseDelaySec, maxDelaySec)
	return b.applyJitter(time.Duration(delaySec * float64(time.Second)))
}

func (b *Backoff) applyJitter(d time.Duration) time.Duration {
	if b.config.Jitter <= 0 {
		return d
	}

	b.mu.Lock()
	randomFactor := (b.rng.Float64() * 2.0) - 1.0 // -1.0 to +1.0
	b.mu.Unlock()

	jitterFactor := 1.0 + (randomFactor * b.config.Jitter)
	result := time.Duration(float64(d) * jitterFactor)
	if result < 0 {
		return 0
	}
	return result
}

// MaxRetries returns the configured maximum retry attempts.
func (b *Backoff) MaxRetries() int {
	return b.config.MaxRetries
}
