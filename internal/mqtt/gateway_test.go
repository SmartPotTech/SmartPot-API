package mqtt

import "testing"

func TestShardForKeepsEachCropInOneQueue(t *testing.T) {
	g := &Gateway{topics: NewTopicResolver("smartpot/v1"), shards: make([]chan mqttJob, 8)}
	crop := "66f5a1000000000000000101"
	want := g.shardFor("smartpot/v1/" + crop + "/telemetry")
	for _, suffix := range []string{"/status", "/commands/ack"} {
		if got := g.shardFor("smartpot/v1/" + crop + suffix); got != want {
			t.Fatalf("el tópico %s cayó en la cola %d y no en %d", suffix, got, want)
		}
	}
}
