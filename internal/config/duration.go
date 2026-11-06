package config

import (
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"
)

// ParseDuration acepta duraciones ISO-8601 (por ejemplo "P7D", "PT5M", "PT1.5S"),
// las mismas que usa la configuración de la API en Java (Duration.parse). Como
// comodidad para operadores, también admite el formato nativo de Go ("5m", "2h30m").
func ParseDuration(s string) (time.Duration, error) {
	raw := strings.TrimSpace(s)
	if raw == "" {
		return 0, errors.New("empty duration")
	}
	upper := strings.ToUpper(raw)
	if strings.HasPrefix(upper, "P") || strings.HasPrefix(upper, "-P") || strings.HasPrefix(upper, "+P") {
		return parseISO(upper)
	}
	return time.ParseDuration(raw)
}

// parseISO interpreta el subconjunto de ISO-8601 que admite java.time.Duration:
// P[nD]T[nH][nM][nS], con signo opcional y fracción en los segundos.
func parseISO(s string) (time.Duration, error) {
	neg := false
	switch s[0] {
	case '-':
		neg = true
		s = s[1:]
	case '+':
		s = s[1:]
	}
	if s == "" || s[0] != 'P' {
		return 0, errors.New("invalid ISO-8601 duration: missing 'P'")
	}
	s = s[1:]
	if s == "" {
		return 0, errors.New("invalid ISO-8601 duration: empty after 'P'")
	}

	var total time.Duration
	inTime := false
	for len(s) > 0 {
		if s[0] == 'T' {
			if inTime {
				return 0, errors.New("invalid ISO-8601 duration: duplicate 'T'")
			}
			inTime = true
			s = s[1:]
			if s == "" {
				return 0, errors.New("invalid ISO-8601 duration: nothing after 'T'")
			}
			continue
		}

		i := 0
		for i < len(s) && ((s[i] >= '0' && s[i] <= '9') || s[i] == '.') {
			i++
		}
		if i == 0 {
			return 0, fmt.Errorf("invalid ISO-8601 duration: expected number in %q", s)
		}
		if i >= len(s) {
			return 0, errors.New("invalid ISO-8601 duration: number without unit")
		}
		value, err := strconv.ParseFloat(s[:i], 64)
		if err != nil {
			return 0, fmt.Errorf("invalid ISO-8601 duration: number %q", s[:i])
		}
		unit := s[i]
		s = s[i+1:]

		var factor time.Duration
		if inTime {
			switch unit {
			case 'H':
				factor = time.Hour
			case 'M':
				factor = time.Minute
			case 'S':
				factor = time.Second
			default:
				return 0, fmt.Errorf("invalid ISO-8601 duration: time unit %q", string(unit))
			}
		} else {
			switch unit {
			case 'D':
				factor = 24 * time.Hour
			default:
				return 0, fmt.Errorf("invalid ISO-8601 duration: date unit %q", string(unit))
			}
		}
		total += time.Duration(value * float64(factor))
	}

	if neg {
		total = -total
	}
	return total, nil
}
