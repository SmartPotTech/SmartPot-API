package ai

import (
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
)

// Comprueba que si la clave ai-eval ya existe en caché, OnReading descarta la evaluación
// de inmediato sin consultar la base de datos ni adquirir el semáforo.
func TestAgentEarlyIntervalSkip(t *testing.T) {
	store := cache.NewStore(nil)
	cropID := "507f1f77bcf86cd799439011"
	store.Put("ai-eval:"+cropID, "active", time.Minute)

	client := NewClient(config.AI{Enabled: true, Token: "test-token-long-enough-32-chars-long", BaseURL: "http://127.0.0.1:8000"}, store, time.Now)
	agent := NewAutomationAgent(nil, nil, nil, store, client, nil, time.Minute, time.Hour)

	// Disparar OnReading para un cultivo en cooldown
	event := readings.ReadingRecordedEvent{
		CropID: cropID,
		Reading: readings.Reading{
			Measures: readings.Measures{},
		},
	}

	// No debe bloquearse ni causar pánico por tener cropsSvc en nil
	agent.OnReading(event)
}

// Comprueba que cuando el semáforo de 32 slots está saturado, una nueva evaluación
// se descarta de forma no bloqueante.
func TestAgentSemaphoreLimitsConcurrency(t *testing.T) {
	store := cache.NewStore(nil)
	client := NewClient(config.AI{Enabled: true, Token: "test-token-long-enough-32-chars-long", BaseURL: "http://127.0.0.1:8000"}, store, time.Now)
	agent := NewAutomationAgent(nil, nil, nil, store, client, nil, time.Minute, time.Hour)

	// Llenar los 32 slots del semáforo
	for i := 0; i < 32; i++ {
		agent.sem <- struct{}{}
	}

	cropID := "507f1f77bcf86cd799439012"
	event := readings.ReadingRecordedEvent{
		CropID: cropID,
		Reading: readings.Reading{
			Measures: readings.Measures{},
		},
	}

	// No debe bloquearse ni causar pánico por cropsSvc nil
	agent.OnReading(event)

	// Vaciar semáforo
	for i := 0; i < 32; i++ {
		<-agent.sem
	}
}
