package mqtt

import (
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"sync"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/commands"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
)

// Paquete compacto de telemetría: alternativa binaria al JSON del contrato v1, en el mismo tópico.
//
// v2, 27 bytes, big-endian:
//
//	[0] 'P' (0x50) · [1] versión 2 · [2-3] secuencia u16 · [4-7] época u32 en segundos (0 = sin hora)
//	[8-9] máscara de presencia u16: bit 0 temperatura, 1 humedad, 2 luz, 3 pH, 4 TDS, 5 presión, 6 sustrato
//	[10-11] temperatura i16 ×100 °C · [12-13] humedad u16 ×100 % · [14-17] luz u32 lux · [18-19] pH u16 ×100
//	[20-21] TDS u16 ppm · [22-23] presión u16 ×10 hPa · [24-25] sustrato u16 ×100 % · [26] CRC-8 (0x07) de [0-25]
//
// Un campo sin su bit es un sensor ausente (como omitirlo en el JSON); un 0 con su bit es una lectura real.
// v1 (22 bytes, sin máscara ni presión y luz u16) se sigue aceptando.
const (
	CompactBinaryMagic byte = 0x50
	compactV1Size           = 22
	compactV2Size           = 27
)

// Bits de la máscara de presencia del paquete v2.
const (
	PresentTemperature uint16 = 1 << iota
	PresentHumidity
	PresentBrightness
	PresentPh
	PresentTds
	PresentAtmosphere
	PresentSoilMoisture
)

// CRC8 calcula el checksum CRC-8 (polinomio 0x07) sobre el buffer.
func CRC8(data []byte) byte {
	var crc byte = 0x00
	for _, b := range data {
		crc ^= b
		for range 8 {
			if (crc & 0x80) != 0 {
				crc = (crc << 1) ^ 0x07
			} else {
				crc <<= 1
			}
		}
	}
	return crc
}

// isCompact indica si la carga parece un paquete binario (el JSON nunca empieza por 'P').
func isCompact(data []byte) bool {
	return len(data) >= compactV1Size && data[0] == CompactBinaryMagic
}

// ParseCompactBinary decodifica un paquete compacto v2 (o v1) verificando su CRC-8.
func ParseCompactBinary(data []byte) (readings.Measures, error) {
	if len(data) < 2 || data[0] != CompactBinaryMagic {
		return readings.Measures{}, errors.New("invalid magic byte in compact packet")
	}
	var m readings.Measures
	switch {
	case len(data) == compactV2Size && data[1] == 2:
		if CRC8(data[:compactV2Size-1]) != data[compactV2Size-1] {
			return readings.Measures{}, errors.New("CRC-8 checksum mismatch in compact packet")
		}
		mask := binary.BigEndian.Uint16(data[8:10])
		u16 := func(i int) float64 { return float64(binary.BigEndian.Uint16(data[i : i+2])) }
		set := func(bit uint16, target **float64, value float64) {
			if mask&bit != 0 {
				*target = &value
			}
		}
		set(PresentTemperature, &m.Temperature, float64(int16(binary.BigEndian.Uint16(data[10:12])))/100)
		set(PresentHumidity, &m.Humidity, u16(12)/100)
		set(PresentBrightness, &m.Brightness, float64(binary.BigEndian.Uint32(data[14:18])))
		set(PresentPh, &m.Ph, u16(18)/100)
		set(PresentTds, &m.Tds, u16(20))
		set(PresentAtmosphere, &m.Atmosphere, u16(22)/10)
		set(PresentSoilMoisture, &m.SoilMoisture, u16(24)/100)
	case len(data) == compactV1Size:
		if CRC8(data[:compactV1Size-1]) != data[compactV1Size-1] {
			return readings.Measures{}, errors.New("CRC-8 checksum mismatch in compact packet")
		}
		temp := float64(int16(binary.BigEndian.Uint16(data[8:10]))) / 100
		hum := float64(binary.BigEndian.Uint16(data[10:12])) / 100
		ph := float64(binary.BigEndian.Uint16(data[12:14])) / 100
		tds := float64(binary.BigEndian.Uint16(data[14:16]))
		moist := float64(binary.BigEndian.Uint16(data[16:18])) / 100
		bright := float64(binary.BigEndian.Uint16(data[18:20]))
		m = readings.Measures{Temperature: &temp, Humidity: &hum, Ph: &ph, Tds: &tds, SoilMoisture: &moist, Brightness: &bright}
	default:
		return readings.Measures{}, errors.New("invalid length or version for compact packet")
	}
	if err := readings.ValidateRanges(m); err != nil {
		return readings.Measures{}, err
	}
	return m, nil
}

const maxPayload = 4096

// Pool para reutilizar mapas de deserialización y reducir la presión sobre el GC en el hot-path MQTT.
var mapPool = sync.Pool{
	New: func() any {
		return make(map[string]any, 16)
	},
}

// ParseTelemetry lee la telemetría del dispositivo (soporta formato JSON y paquete binario compacto de 22 bytes).
func ParseTelemetry(payload string) (readings.Measures, error) {
	bytesPayload := []byte(payload)
	if isCompact(bytesPayload) {
		return ParseCompactBinary(bytesPayload)
	}

	if strings.TrimSpace(payload) == "" || len(payload) > maxPayload {
		return readings.Measures{}, errors.New("mensaje vacío o demasiado grande")
	}

	root := mapPool.Get().(map[string]any)
	clear(root)
	defer mapPool.Put(root)

	if err := json.Unmarshal(bytesPayload, &root); err != nil {
		return readings.Measures{}, errors.New("el mensaje no es un objeto JSON válido")
	}

	m := readings.Measures{}
	for field, target := range map[string]**float64{
		"temperature":  &m.Temperature,
		"humidity":     &m.Humidity,
		"brightness":   &m.Brightness,
		"ph":           &m.Ph,
		"tds":          &m.Tds,
		"atmosphere":   &m.Atmosphere,
		"soilMoisture": &m.SoilMoisture,
	} {
		value, err := number(root, field)
		if err != nil {
			return readings.Measures{}, err
		}
		*target = value
	}
	if err := readings.ValidateRanges(m); err != nil {
		return readings.Measures{}, err
	}
	return m, nil
}

// ParseAck lee la confirmación del dispositivo. status: EXECUTED o FAILED.
func ParseAck(payload string) (commands.AckMessage, error) {
	if strings.TrimSpace(payload) == "" || len(payload) > maxPayload {
		return commands.AckMessage{}, errors.New("mensaje vacío o demasiado grande")
	}

	root := mapPool.Get().(map[string]any)
	clear(root)
	defer mapPool.Put(root)

	if err := json.Unmarshal([]byte(payload), &root); err != nil {
		return commands.AckMessage{}, errors.New("el mensaje no es un objeto JSON válido")
	}

	status := strings.ToUpper(stringField(root, "status"))
	if status != "EXECUTED" && status != "FAILED" {
		return commands.AckMessage{}, fmt.Errorf("estado de ACK desconocido: %s", status)
	}
	return commands.AckMessage{ID: stringField(root, "id"), Status: status, Message: stringField(root, "message")}, nil
}

func number(root map[string]any, field string) (*float64, error) {
	value, ok := root[field]
	if !ok || value == nil {
		return nil, nil
	}
	switch v := value.(type) {
	case float64:
		return &v, nil
	case string:
		f, err := strconv.ParseFloat(strings.TrimSpace(v), 64)
		if err != nil {
			return nil, fmt.Errorf("el campo %s no es numérico", field)
		}
		return &f, nil
	default:
		return nil, fmt.Errorf("el campo %s no es numérico", field)
	}
}

func stringField(root map[string]any, field string) string {
	if v, ok := root[field].(string); ok {
		return v
	}
	return ""
}
