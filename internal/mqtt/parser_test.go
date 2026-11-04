package mqtt

import (
	"encoding/binary"
	"testing"
)

func TestParseTelemetry_JSON(t *testing.T) {
	jsonPayload := `{"temperature": 24.5, "humidity": 65.0, "ph": 6.8, "tds": 550, "soilMoisture": 45.0, "brightness": 12000}`
	m, err := ParseTelemetry(jsonPayload)
	if err != nil {
		t.Fatalf("ParseTelemetry JSON failed: %v", err)
	}
	if m.Temperature == nil || *m.Temperature != 24.5 {
		t.Errorf("expected temp 24.5, got %v", m.Temperature)
	}
	if m.Humidity == nil || *m.Humidity != 65.0 {
		t.Errorf("expected humidity 65.0, got %v", m.Humidity)
	}
	if m.Ph == nil || *m.Ph != 6.8 {
		t.Errorf("expected ph 6.8, got %v", m.Ph)
	}
	if m.Tds == nil || *m.Tds != 550.0 {
		t.Errorf("expected tds 550, got %v", m.Tds)
	}
}

func TestParseCompactBinary_Valid(t *testing.T) {
	buf := make([]byte, 22)
	buf[0] = CompactBinaryMagic                         // 0x50
	buf[1] = 0x01                                       // version 1
	binary.BigEndian.PutUint16(buf[2:4], 42)            // seqId
	binary.BigEndian.PutUint32(buf[4:8], 1700000000)    // timestamp
	binary.BigEndian.PutUint16(buf[8:10], uint16(2350)) // temp = 23.5 C
	binary.BigEndian.PutUint16(buf[10:12], 6200)        // hum = 62.0 %
	binary.BigEndian.PutUint16(buf[12:14], 650)         // ph = 6.50
	binary.BigEndian.PutUint16(buf[14:16], 480)         // tds = 480 ppm
	binary.BigEndian.PutUint16(buf[16:18], 5500)        // moisture = 55.0 %
	binary.BigEndian.PutUint16(buf[18:20], 15000)       // bright = 15000 lux
	buf[21] = CRC8(buf[:21])

	m, err := ParseTelemetry(string(buf))
	if err != nil {
		t.Fatalf("ParseTelemetry compact binary failed: %v", err)
	}
	if m.Temperature == nil || *m.Temperature != 23.5 {
		t.Errorf("expected temp 23.5, got %v", m.Temperature)
	}
	if m.Humidity == nil || *m.Humidity != 62.0 {
		t.Errorf("expected humidity 62.0, got %v", m.Humidity)
	}
	if m.Ph == nil || *m.Ph != 6.5 {
		t.Errorf("expected ph 6.5, got %v", m.Ph)
	}
	if m.Tds == nil || *m.Tds != 480.0 {
		t.Errorf("expected tds 480, got %v", m.Tds)
	}
	if m.SoilMoisture == nil || *m.SoilMoisture != 55.0 {
		t.Errorf("expected moisture 55.0, got %v", m.SoilMoisture)
	}
	if m.Brightness == nil || *m.Brightness != 15000.0 {
		t.Errorf("expected brightness 15000, got %v", m.Brightness)
	}
}

func TestParseCompactBinary_CorruptCRC(t *testing.T) {
	buf := make([]byte, 22)
	buf[0] = CompactBinaryMagic
	buf[1] = 0x01
	buf[21] = 0xAA // Bad CRC

	_, err := ParseCompactBinary(buf)
	if err == nil {
		t.Fatal("expected error for corrupted CRC, got nil")
	}
}

func TestParseCompactBinary_BadMagic(t *testing.T) {
	buf := make([]byte, 22)
	buf[0] = 0xFF // Bad magic
	buf[21] = CRC8(buf[:21])

	_, err := ParseCompactBinary(buf)
	if err == nil {
		t.Fatal("expected error for bad magic, got nil")
	}
}

func TestParseCompactBinary_InvalidLength(t *testing.T) {
	buf := make([]byte, 20)
	_, err := ParseCompactBinary(buf)
	if err == nil {
		t.Fatal("expected error for invalid length, got nil")
	}
}

// compactV2 arma un paquete v2 según el formato documentado en parser.go.
func compactV2(mask uint16, temp int16, hum, ph, tds, atm, moist uint16, lux uint32) []byte {
	buf := make([]byte, compactV2Size)
	buf[0], buf[1] = CompactBinaryMagic, 2
	binary.BigEndian.PutUint16(buf[2:4], 7)
	binary.BigEndian.PutUint32(buf[4:8], 1_793_400_000)
	binary.BigEndian.PutUint16(buf[8:10], mask)
	binary.BigEndian.PutUint16(buf[10:12], uint16(temp))
	binary.BigEndian.PutUint16(buf[12:14], hum)
	binary.BigEndian.PutUint32(buf[14:18], lux)
	binary.BigEndian.PutUint16(buf[18:20], ph)
	binary.BigEndian.PutUint16(buf[20:22], tds)
	binary.BigEndian.PutUint16(buf[22:24], atm)
	binary.BigEndian.PutUint16(buf[24:26], moist)
	buf[26] = CRC8(buf[:26])
	return buf
}

func TestParseCompactBinaryV2KeepsZerosAndMissingSensors(t *testing.T) {
	mask := PresentTemperature | PresentBrightness | PresentAtmosphere | PresentHumidity
	m, err := ParseTelemetry(string(compactV2(mask, 0, 6550, 0, 0, 10132, 0, 0)))
	if err != nil {
		t.Fatal(err)
	}
	if m.Temperature == nil || *m.Temperature != 0 || m.Brightness == nil || *m.Brightness != 0 {
		t.Fatalf("0 °C y 0 lux son lecturas reales: %+v", m)
	}
	if m.Atmosphere == nil || *m.Atmosphere != 1013.2 || m.Humidity == nil || *m.Humidity != 65.5 {
		t.Fatalf("presión o humedad mal decodificadas: %+v", m)
	}
	if m.Ph != nil || m.Tds != nil || m.SoilMoisture != nil {
		t.Fatalf("los sensores sin bit deben quedar ausentes: %+v", m)
	}
}

func TestParseCompactBinaryV2ReadsFullSunlight(t *testing.T) {
	m, err := ParseCompactBinary(compactV2(PresentBrightness|PresentTemperature, -250, 0, 0, 0, 0, 0, 98_000))
	if err != nil {
		t.Fatal(err)
	}
	if *m.Brightness != 98_000 || *m.Temperature != -2.5 {
		t.Fatalf("luz o temperatura negativa mal decodificadas: %+v", m)
	}
}

func TestParseCompactBinaryV2RejectsCorruptCRC(t *testing.T) {
	buf := compactV2(PresentTemperature, 2100, 0, 0, 0, 0, 0, 0)
	buf[11] ^= 0xFF
	if _, err := ParseCompactBinary(buf); err == nil {
		t.Fatal("mismatched CRC should be rejected")
	}
}
