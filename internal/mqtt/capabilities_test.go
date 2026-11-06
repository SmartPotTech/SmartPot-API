package mqtt

import (
	"encoding/json"
	"strings"
	"testing"
)

// canonicalSensor es un sensor válido que se reutiliza en varias cargas.
const canonicalSensor = `{"key":"temperature","unit":"C","resolution":0.01,"range":[-20,60]}`

// validSensorsJSON arma los siete sensores canónicos dentro de los límites del servidor.
func validSensorsJSON() string {
	return strings.Join([]string{
		canonicalSensor,
		`{"key":"humidity","unit":"%","resolution":0.1,"range":[0,100]}`,
		`{"key":"brightness","unit":"lux","resolution":1,"range":[0,200000]}`,
		`{"key":"ph","unit":"pH","resolution":0.01,"range":[0,14]}`,
		`{"key":"tds","unit":"ppm","resolution":1,"range":[0,10000]}`,
		`{"key":"atmosphere","unit":"hPa","resolution":0.1,"range":[300,1100]}`,
		`{"key":"soilMoisture","unit":"%","resolution":0.01,"range":[0,100]}`,
	}, ",")
}

// validActuatorsJSON arma los cuatro actuadores que publican los bordes.
func validActuatorsJSON() string {
	return `"WATER_PUMP","UV_LIGHT","FAN","HUMIDIFIER"`
}

// capabilitiesPayload arma una declaración completa con los sensores y actuadores indicados.
func capabilitiesPayload(sensors, actuators string) string {
	return `{"schema":1,` +
		`"device":{"firmware":"smartpot-iot/0.1.0","hardware":"esp32-devkit-c-v4",` +
		`"telemetry":{"formats":["json-v1","compact-v2"],"compactBytes":27}},` +
		`"sensors":[` + sensors + `],"actuators":[` + actuators + `]}`
}

// validCapabilitiesPayload devuelve la carga canónica de siete sensores y cuatro actuadores.
func validCapabilitiesPayload() string {
	return capabilitiesPayload(validSensorsJSON(), validActuatorsJSON())
}

func TestParseCapabilitiesAcceptsCanonicalPayload(t *testing.T) {
	payload := validCapabilitiesPayload()
	raw, err := ParseCapabilities(payload)
	if err != nil {
		t.Fatalf("valid capabilities rejected: %v", err)
	}
	if string(raw) != payload {
		t.Fatal("the original raw JSON must be returned unchanged")
	}
	var parsed capabilitiesEnvelope
	if err := json.Unmarshal(raw, &parsed); err != nil {
		t.Fatalf("raw capabilities are not valid JSON: %v", err)
	}
	if len(parsed.Sensors) != 7 || len(parsed.Actuators) != 4 {
		t.Fatalf("expected 7 sensors and 4 actuators, got %d and %d",
			len(parsed.Sensors), len(parsed.Actuators))
	}
}

func TestParseCapabilitiesAcceptsCompactV2WithoutSize(t *testing.T) {
	payload := strings.Replace(validCapabilitiesPayload(), `,"compactBytes":27`, "", 1)
	if _, err := ParseCapabilities(payload); err != nil {
		t.Fatalf("compactBytes is optional for compact-v2: %v", err)
	}
}

func TestParseCapabilitiesRejectsInvalidPayloads(t *testing.T) {
	base := validCapabilitiesPayload()
	replace := func(old, replacement string) string {
		return strings.Replace(base, old, replacement, 1)
	}
	tooManySensors := make([]string, maxCapabilitySensors+1)
	for i := range tooManySensors {
		tooManySensors[i] = canonicalSensor
	}
	tooManyActuators := make([]string, maxCapabilityActuators+1)
	for i := range tooManyActuators {
		tooManyActuators[i] = `"WATER_PUMP"`
	}
	oversized := strings.TrimSuffix(base, "}") + `,"padding":"` +
		strings.Repeat("a", maxCapabilitiesBytes) + `"}`

	cases := []struct {
		name    string
		payload string
		want    string
	}{
		{"empty payload", "", "empty or oversized"},
		{"blank payload", "   \n\t", "empty or oversized"},
		{"invalid JSON", "{", "valid JSON"},
		{"schema other than one", replace(`"schema":1`, `"schema":2`), "schema"},
		{"missing schema", replace(`"schema":1,`, ""), "schema"},
		{"missing firmware", replace(`"smartpot-iot/0.1.0"`, `""`), "firmware"},
		{"missing hardware", replace(`"esp32-devkit-c-v4"`, `""`), "hardware"},
		{"empty formats", replace(`["json-v1","compact-v2"]`, `[]`), "formats"},
		{"unknown format", replace(`"json-v1"`, `"xml-v1"`), "telemetry format"},
		{"wrong compact size", replace(`"compactBytes":27`, `"compactBytes":28`), "compactBytes"},
		{"duplicate sensor", replace(canonicalSensor, `{"key":"humidity","unit":"%","resolution":0.1,"range":[0,100]}`), "duplicate sensor"},
		{"unknown sensor", replace(`"key":"temperature"`, `"key":"pressure"`), "unknown sensor"},
		{"empty sensor key", replace(`"key":"temperature"`, `"key":""`), "sensor key"},
		{"empty unit", replace(`"unit":"C"`, `"unit":""`), "unit"},
		{"zero resolution", replace(`"resolution":0.01`, `"resolution":0`), "resolution"},
		{"negative resolution", replace(`"resolution":0.01`, `"resolution":-0.01`), "resolution"},
		{"range with one element", replace(`"range":[-20,60]`, `"range":[-20]`), "two-element"},
		{"range with null", replace(`"range":[-20,60]`, `"range":[null,60]`), "two-element"},
		{"reversed range", replace(`"range":[-20,60]`, `"range":[60,-20]`), "lower than maximum"},
		{"range outside server limits", replace(`"range":[-20,60]`, `"range":[-30,60]`), "API limits"},
		{"too many sensors", capabilitiesPayload(strings.Join(tooManySensors, ","), validActuatorsJSON()), "too many sensors"},
		{"too many actuators", capabilitiesPayload(validSensorsJSON(), strings.Join(tooManyActuators, ",")), "too many actuators"},
		{"unknown actuator", replace(`"WATER_PUMP"`, `"LASER"`), "unknown actuator"},
		{"duplicate actuator", replace(`"UV_LIGHT"`, `"WATER_PUMP"`), "duplicate actuator"},
		{"oversized payload", oversized, "oversized"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := ParseCapabilities(tc.payload); err == nil {
				t.Fatalf("expected an error containing %q, got nil", tc.want)
			} else if !strings.Contains(err.Error(), tc.want) {
				t.Fatalf("error %q does not contain %q", err, tc.want)
			}
		})
	}
}
