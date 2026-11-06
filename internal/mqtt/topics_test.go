package mqtt

import "testing"

func TestTopicResolverBuildsAndParsesCapabilities(t *testing.T) {
	const crop = "66f5a1000000000000000101"
	r := NewTopicResolver("smartpot/v1")

	topic := r.Capabilities(crop)
	if want := "smartpot/v1/" + crop + "/capabilities"; topic != want {
		t.Fatalf("Capabilities topic = %q, want %q", topic, want)
	}

	parsed, ok := r.Parse(topic)
	if !ok {
		t.Fatalf("capabilities topic %q must parse", topic)
	}
	if parsed.CropID != crop || parsed.Kind != KindCapabilities {
		t.Fatalf("unexpected parsed topic: %+v", parsed)
	}
}

func TestSubscriptionsIncludeCapabilities(t *testing.T) {
	r := NewTopicResolver("smartpot/v1")
	want := "smartpot/v1/+/capabilities"
	for _, sub := range r.Subscriptions() {
		if sub == want {
			return
		}
	}
	t.Fatalf("Subscriptions() must include %q, got %v", want, r.Subscriptions())
}

func TestParseRejectsMalformedCapabilitiesTopics(t *testing.T) {
	r := NewTopicResolver("smartpot/v1")
	for _, topic := range []string{
		"smartpot/v1/not-an-objectid/capabilities",
		"smartpot/v1/66f5a1000000000000000101/capabilities/extra",
		"smartpot/v1/66f5a1000000000000000101/",
	} {
		if parsed, ok := r.Parse(topic); ok {
			t.Fatalf("topic %q must not parse, got %+v", topic, parsed)
		}
	}
}
