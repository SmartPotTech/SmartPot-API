package mqtt

import (
	"log/slog"
	"testing"
	"time"
)

func TestShardForKeepsEachCropInOneQueue(t *testing.T) {
	g := &Gateway{topics: NewTopicResolver("smartpot/v1"), shards: make([]chan mqttJob, 8)}
	crop := "66f5a1000000000000000101"
	want := g.shardFor("smartpot/v1/" + crop + "/telemetry")
	for _, suffix := range []string{"/status", "/commands/ack"} {
		if got := g.shardFor("smartpot/v1/" + crop + suffix); got != want {
			t.Fatalf("topic %s landed in queue %d instead of %d", suffix, got, want)
		}
	}
}

// fakeMessage implements paho.Message for the retained-message tests.
type fakeMessage struct {
	topic    string
	payload  string
	retained bool
}

func (m fakeMessage) Duplicate() bool   { return false }
func (m fakeMessage) Qos() byte         { return 1 }
func (m fakeMessage) Retained() bool    { return m.retained }
func (m fakeMessage) Topic() string     { return m.topic }
func (m fakeMessage) MessageID() uint16 { return 0 }
func (m fakeMessage) Payload() []byte   { return []byte(m.payload) }
func (m fakeMessage) Ack()              {}

func newTestGateway() (*Gateway, chan mqttJob) {
	shard := make(chan mqttJob, 1)
	return &Gateway{
		topics:     NewTopicResolver("smartpot/v1"),
		shards:     []chan mqttJob{shard},
		stopSignal: make(chan struct{}),
		log:        slog.New(slog.DiscardHandler),
		router:     func(string, string) {},
	}, shard
}

func TestOnMessageDropsRetainedTelemetryAndAck(t *testing.T) {
	const crop = "66f5a1000000000000000101"
	g, shard := newTestGateway()

	for _, topic := range []string{
		"smartpot/v1/" + crop + "/telemetry",
		"smartpot/v1/" + crop + "/commands/ack",
	} {
		g.onMessage(nil, fakeMessage{topic: topic, payload: "stale", retained: true})
		select {
		case job := <-shard:
			t.Fatalf("retained message on %s must be ignored, got %+v", topic, job)
		case <-time.After(50 * time.Millisecond):
		}
	}
}

func TestOnMessageProcessesRetainedStatusAndLiveTelemetry(t *testing.T) {
	const crop = "66f5a1000000000000000101"
	g, shard := newTestGateway()

	g.onMessage(nil, fakeMessage{topic: "smartpot/v1/" + crop + "/status", payload: "offline", retained: true})
	select {
	case job := <-shard:
		if job.topic != "smartpot/v1/"+crop+"/status" {
			t.Fatalf("unexpected job: %+v", job)
		}
	case <-time.After(time.Second):
		t.Fatal("retained status (LWT) must be processed")
	}

	g.onMessage(nil, fakeMessage{topic: "smartpot/v1/" + crop + "/telemetry", payload: "21.5", retained: false})
	select {
	case <-shard:
	case <-time.After(time.Second):
		t.Fatal("live telemetry must be queued")
	}
}

func TestOnMessageIgnoresMessagesAfterStop(t *testing.T) {
	const crop = "66f5a1000000000000000101"
	g, shard := newTestGateway()
	g.stopped.Store(true)
	g.onMessage(nil, fakeMessage{topic: "smartpot/v1/" + crop + "/telemetry", payload: "x"})
	select {
	case job := <-shard:
		t.Fatalf("messages must not be accepted during shutdown, got %+v", job)
	case <-time.After(50 * time.Millisecond):
	}
}
