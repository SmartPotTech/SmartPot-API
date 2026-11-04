package ai

import (
	"testing"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
)

func f(v float64) *float64 { return &v }

func TestWithinDeadband(t *testing.T) {
	base := readings.Measures{Temperature: f(22), Humidity: f(60), Ph: f(6.2), Brightness: f(10_000)}
	cases := []struct {
		name string
		cur  readings.Measures
		want bool
	}{
		{"cambios despreciables", readings.Measures{Temperature: f(22.05), Humidity: f(60.3), Ph: f(6.22), Brightness: f(10_300)}, true},
		{"temperature rises", readings.Measures{Temperature: f(22.2), Humidity: f(60), Ph: f(6.2), Brightness: f(10_000)}, false},
		{"light changes more than 5 percent", readings.Measures{Temperature: f(22), Humidity: f(60), Ph: f(6.2), Brightness: f(11_000)}, false},
		{"sensor appears", readings.Measures{Temperature: f(22), Humidity: f(60), Ph: f(6.2), Brightness: f(10_000), Tds: f(700)}, false},
		{"sensor disappears", readings.Measures{Temperature: f(22), Humidity: f(60), Brightness: f(10_000)}, false},
	}
	for _, tc := range cases {
		if got := withinDeadband(base, tc.cur); got != tc.want {
			t.Errorf("%s: got %v", tc.name, got)
		}
	}
}
