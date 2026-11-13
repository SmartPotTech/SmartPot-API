package events

import "testing"

type sampleEvent struct {
	value string
}

func TestPublishIsolatesPanickingHandlers(t *testing.T) {
	bus := NewBus()
	secondCalled := ""

	Subscribe(bus, func(sampleEvent) {
		panic("boom")
	})
	Subscribe(bus, func(e sampleEvent) {
		secondCalled = e.value
	})

	bus.Publish(sampleEvent{value: "ok"})

	if secondCalled != "ok" {
		t.Fatalf("second handler must still run after first panics; got %q", secondCalled)
	}
}
