package channels

import (
	"encoding/json"
	"testing"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
)

func TestDeliveryUnmarshalJSON(t *testing.T) {
	cases := []struct {
		in    string
		want  Delivery
		valid bool
	}{
		{`"INSTANT"`, DeliveryInstant, true},
		{`"DIGEST"`, DeliveryDigest, true},
		{`"instant"`, "", false},
		{`"WEEKLY"`, "", false},
		{`""`, "", false},
	}
	for _, c := range cases {
		var d Delivery
		err := json.Unmarshal([]byte(c.in), &d)
		if c.valid {
			if err != nil {
				t.Errorf("%s: error inesperado: %v", c.in, err)
			} else if d != c.want {
				t.Errorf("%s: got %q, expected %q", c.in, d, c.want)
			}
			continue
		}
		if err == nil {
			t.Errorf("%s: expected error, got none", c.in)
		}
	}
}

// Un modo de entrega desconocido en el cuerpo debe fallar la deserialización
// (mismo 400 "El cuerpo de la solicitud no es un JSON válido" que Jackson).
func TestCropChannelUpdateRequestRejectsUnknownDelivery(t *testing.T) {
	var req CropChannelUpdateRequest
	if err := json.Unmarshal([]byte(`{"delivery":"MONTHLY"}`), &req); err == nil {
		t.Fatal("expected error deserializing invalid delivery mode")
	}
	if err := json.Unmarshal([]byte(`{"delivery":"DIGEST","digestHours":3}`), &req); err != nil {
		t.Fatalf("valid body should not fail: %v", err)
	}
	if req.Delivery == nil || *req.Delivery != DeliveryDigest {
		t.Fatalf("delivery mal deserializado: %+v", req.Delivery)
	}
}

func TestCropChannelUpdateRequestValidate(t *testing.T) {
	hours := func(n int) *int { return &n }
	at := func(s string) *string { return &s }
	cases := []struct {
		name      string
		req       CropChannelUpdateRequest
		badFields []string
	}{
		{"empty", CropChannelUpdateRequest{}, nil},
		{"min hours", CropChannelUpdateRequest{DigestHours: hours(1)}, nil},
		{"max hours", CropChannelUpdateRequest{DigestHours: hours(24)}, nil},
		{"hours below min", CropChannelUpdateRequest{DigestHours: hours(0)}, []string{"digestHours"}},
		{"hours above max", CropChannelUpdateRequest{DigestHours: hours(25)}, []string{"digestHours"}},
		{"empty time", CropChannelUpdateRequest{DailySummaryAt: at("")}, nil},
		{"valid time", CropChannelUpdateRequest{DailySummaryAt: at("07:30")}, nil},
		{"medianoche", CropChannelUpdateRequest{DailySummaryAt: at("00:00")}, nil},
		{"last minute", CropChannelUpdateRequest{DailySummaryAt: at("23:59")}, nil},
		{"time without leading zero", CropChannelUpdateRequest{DailySummaryAt: at("7:30")}, []string{"dailySummaryAt"}},
		{"hora 24", CropChannelUpdateRequest{DailySummaryAt: at("24:00")}, []string{"dailySummaryAt"}},
		{"invalid minute", CropChannelUpdateRequest{DailySummaryAt: at("12:60")}, []string{"dailySummaryAt"}},
		{"text", CropChannelUpdateRequest{DailySummaryAt: at("tomorrow")}, []string{"dailySummaryAt"}},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			res := c.req.Validate()
			if len(res) != len(c.badFields) {
				t.Fatalf("error fields %v, expected %v", res, c.badFields)
			}
			for _, f := range c.badFields {
				if _, ok := res[f]; !ok {
					t.Errorf("missing error for field %q (%v)", f, res)
				}
			}
		})
	}
}

func TestChannelLinkUpdateRequestValidate(t *testing.T) {
	events := func(ts ...notifications.Type) *[]notifications.Type { return &ts }
	five := events(notifications.TypeInfo, notifications.TypeAlert, notifications.TypeCommand,
		notifications.TypeDevice, notifications.TypeAI)
	six := events(notifications.TypeInfo, notifications.TypeAlert, notifications.TypeCommand,
		notifications.TypeDevice, notifications.TypeAI, notifications.TypeInfo)
	if res := (ChannelLinkUpdateRequest{Events: five}).Validate(); len(res) != 0 {
		t.Errorf("five types should be valid: %v", res)
	}
	if res := (ChannelLinkUpdateRequest{Events: six}).Validate(); res["events"] == "" {
		t.Errorf("seis tipos deben dar error en events: %v", res)
	}
	if res := (ChannelLinkUpdateRequest{}).Validate(); len(res) != 0 {
		t.Errorf("empty events should be valid: %v", res)
	}
}

func TestIsShareCode(t *testing.T) {
	cases := map[string]bool{
		"c_abc": true,
		"c_":    true,
		"abc":   false,
		"c":     false,
		"":      false,
		"xc_":   false,
	}
	for code, want := range cases {
		if got := IsShareCode(code); got != want {
			t.Errorf("IsShareCode(%q) = %v, expected %v", code, got, want)
		}
	}
}

// Las claves de Redis son un contrato: deben reproducirse literalmente.
func TestCacheKeyFormats(t *testing.T) {
	if got := codeKey(Telegram, "AbC-1"); got != "channel-link:telegram:AbC-1" {
		t.Errorf("codeKey = %q", got)
	}
	if got := shareKey(Telegram, "c_xyz"); got != "crop-share:telegram:c_xyz" {
		t.Errorf("shareKey = %q", got)
	}
	if got := lower(Telegram); got != "telegram" {
		t.Errorf("lower = %q", got)
	}
}

// Los eventos se serializan en el orden del enum de Java (ordinal), no alfabético.
func TestSortedEventsOrdinalOrder(t *testing.T) {
	in := []notifications.Type{notifications.TypeAI, notifications.TypeInfo, notifications.TypeDevice,
		notifications.TypeAlert, notifications.TypeCommand}
	want := []notifications.Type{notifications.TypeInfo, notifications.TypeAlert, notifications.TypeCommand,
		notifications.TypeDevice, notifications.TypeAI}
	got := sortedEvents(in)
	if len(got) != len(want) {
		t.Fatalf("longitud %d, se esperaba %d", len(got), len(want))
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("orden %v, se esperaba %v", got, want)
		}
	}
}
