package ai

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/actuators"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/commands"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
)

const (
	automaticInterval = 30 * time.Second
	alertCooldown     = time.Hour
	agentNoticeCd     = 30 * time.Minute
	deadbandMaxAge    = 15 * time.Minute
)

// AutomationAgent percibe cada lectura, la hace evaluar por la IA y, con el modo
// automático, ejecuta las acciones propuestas respetando un enfriamiento.
type AutomationAgent struct {
	insights           *InsightService
	commands           *commands.Service
	notifier           *notifications.Service
	cache              cache.Cache
	client             *Client
	crops              *crops.Service
	evaluationInterval time.Duration
	automationCooldown time.Duration
}

// NewAutomationAgent crea el agente.
func NewAutomationAgent(insights *InsightService, commandsSvc *commands.Service, notifier *notifications.Service,
	store cache.Cache, client *Client, cropsSvc *crops.Service, evaluationInterval, automationCooldown time.Duration) *AutomationAgent {
	return &AutomationAgent{insights: insights, commands: commandsSvc, notifier: notifier, cache: store,
		client: client, crops: cropsSvc, evaluationInterval: evaluationInterval, automationCooldown: automationCooldown}
}

// OnReading evalúa el cultivo tras una lectura. Corre en su propia goroutine.
func (a *AutomationAgent) OnReading(event readings.ReadingRecordedEvent) {
	if !a.client.IsEnabled() {
		return
	}
	ctx := context.Background()
	crop, err := a.crops.Find(ctx, event.CropID)
	if err != nil || crop == nil {
		return
	}
	interval := a.evaluationInterval
	if crop.AutomationEnabled {
		interval = automaticInterval
	}
	if !a.cache.SetIfAbsent("ai-eval:"+crop.HexID(), interval) {
		return
	}
	// Banda muerta: si la lectura casi no cambió desde la última evaluación, la IA respondería lo mismo. Como la
	// marca expira, al menos cada deadbandMaxAge se vuelve a evaluar aunque todo siga estable.
	lastKey := "ai-eval-last:" + crop.HexID()
	if raw, ok := a.cache.Get(lastKey); ok {
		var prev readings.Measures
		if json.Unmarshal([]byte(raw), &prev) == nil && withinDeadband(prev, event.Reading.Measures) {
			slog.Debug("evaluation skipped due to deadband", "cropId", crop.HexID())
			return
		}
	}
	insight, err := a.insights.Evaluate(ctx, crop, event.Reading)
	if err != nil {
		var apiErr *apperr.Error
		if errors.As(err, &apiErr) {
			slog.Debug("evaluation skipped", "cropId", crop.HexID(), "err", err)
		} else {
			slog.Warn("agent could not evaluate crop", "cropId", crop.HexID(), "err", err)
		}
		return
	}
	if raw, err := json.Marshal(event.Reading.Measures); err == nil {
		a.cache.Put(lastKey, string(raw), deadbandMaxAge)
	}
	a.alertCriticalFindings(ctx, crop, insight)
	if crop.AutomationEnabled {
		a.act(ctx, crop, insight)
	}
}

func (a *AutomationAgent) alertCriticalFindings(ctx context.Context, crop *crops.Crop, insight *InsightResponse) {
	for _, item := range insight.Diagnosis {
		if !equalsIgnoreCase(item.Severity, "CRITICAL") {
			continue
		}
		message := item.Message
		if item.Recommendation != "" {
			message += " " + item.Recommendation
		}
		_, _ = a.notifier.NotifyOnce(ctx, "diagnosis:"+crop.HexID()+":"+item.Parameter, alertCooldown,
			crop.OwnerID.Hex(), crop.HexID(), notifications.TypeAlert, "Attention needed on "+crop.Name, message)
	}
}

func (a *AutomationAgent) act(ctx context.Context, crop *crops.Crop, insight *InsightResponse) {
	for _, action := range insight.Actions {
		actuatorType, okType := actuators.ParseType(action.Actuator)
		commandAction, okAction := commands.ParseAction(action.Action)
		if !okType || !okAction {
			continue
		}
		cooldownKey := "agent:" + crop.HexID() + ":" + string(actuatorType) + ":" + string(commandAction)
		if !a.cache.SetIfAbsent(cooldownKey, a.automationCooldown) {
			continue
		}
		cmd, ok := a.commands.RequestFromAgent(ctx, crop, actuatorType, commandAction, action.DurationSeconds, action.Reason)
		if ok && cmd != nil {
			_, _ = a.notifier.NotifyOnce(ctx, "agent:"+crop.HexID(), agentNoticeCd, crop.OwnerID.Hex(), crop.HexID(),
				notifications.TypeAI, "The assistant acted on "+crop.Name, action.Reason)
		}
	}
}

func equalsIgnoreCase(a, b string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := 0; i < len(a); i++ {
		ca, cb := a[i], b[i]
		if 'A' <= ca && ca <= 'Z' {
			ca += 'a' - 'A'
		}
		if 'A' <= cb && cb <= 'Z' {
			cb += 'a' - 'A'
		}
		if ca != cb {
			return false
		}
	}
	return true
}
