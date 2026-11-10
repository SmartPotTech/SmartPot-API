package ai

import (
	"context"
	"testing"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/actuators"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

func TestIsChemicalDoser(t *testing.T) {
	// Verifica que solo los dosificadores químicos estén sujetos al presupuesto diario de dosis.
	if !isChemicalDoser(actuators.PhDoser) {
		t.Errorf("expected PhDoser to be chemical doser")
	}
	if !isChemicalDoser(actuators.NutrientDoser) {
		t.Errorf("expected NutrientDoser to be chemical doser")
	}
	if isChemicalDoser(actuators.WaterPump) {
		t.Errorf("expected WaterPump not to be chemical doser")
	}
	if isChemicalDoser(actuators.Fan) {
		t.Errorf("expected Fan not to be chemical doser")
	}
	if isChemicalDoser(actuators.UVLight) {
		t.Errorf("expected UVLight not to be chemical doser")
	}
	if isChemicalDoser(actuators.Humidifier) {
		t.Errorf("expected Humidifier not to be chemical doser")
	}
}

func TestChemicalBudgetSafetyLimit(t *testing.T) {
	// Almacén en memoria para pruebas de presupuesto de seguridad.
	store := cache.NewStore(nil)
	cropID := primitive.NewObjectID()
	crop := &crops.Crop{
		ID:                cropID,
		Name:              "Lettuce",
		AutomationEnabled: true,
	}

	insight := &InsightResponse{
		Actions: []Action{
			{
				Actuator:        "PH_DOSER",
				Action:          "ACTIVATE",
				DurationSeconds: intPtr(3),
				Reason:          "pH is too high",
			},
			{
				Actuator:        "WATER_PUMP",
				Action:          "ACTIVATE",
				DurationSeconds: intPtr(15),
				Reason:          "Substrate moisture is low",
			},
		},
	}

	agent := &AutomationAgent{
		cache:              store,
		automationCooldown: 0, // Sin enfriamiento entre invocaciones de prueba.
	}

	ctx := context.Background()

	// Simulamos 3 dosis químicas dentro del límite de 24h.
	for i := 1; i <= dailyChemicalLimit; i++ {
		budgetKey := "agent:chemical-budget:" + crop.HexID() + ":" + string(actuators.PhDoser)
		doses := store.Increment(budgetKey, dailyChemicalWindow)
		if doses != int64(i) {
			t.Fatalf("expected doses to be %d, got %d", i, doses)
		}
		if doses > dailyChemicalLimit {
			t.Fatalf("dose %d should not exceed daily chemical limit", i)
		}
	}

	// La 4ta dosis debe superar el límite diario de seguridad.
	budgetKey := "agent:chemical-budget:" + crop.HexID() + ":" + string(actuators.PhDoser)
	doses := store.Increment(budgetKey, dailyChemicalWindow)
	if doses <= dailyChemicalLimit {
		t.Fatalf("expected 4th dose to exceed limit, got %d", doses)
	}

	// Comprueba que los actuadores no químicos (bomba de agua) no tienen presupuesto químico.
	waterKey := "agent:chemical-budget:" + crop.HexID() + ":" + string(actuators.WaterPump)
	if val, exists := store.Get(waterKey); exists {
		t.Fatalf("water pump should not consume chemical budget, got %s", val)
	}

	_ = agent
	_ = ctx
	_ = insight
}

func intPtr(v int) *int {
	return &v
}
