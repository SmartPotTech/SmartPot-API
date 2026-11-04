package channels

import (
	"context"
	"fmt"
	"log/slog"
	"math"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/commands"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
)

const digestLines = 8

var digestKinds = map[notifications.Type]string{
	notifications.TypeAlert:   "Alerta",
	notifications.TypeDevice:  "Connection",
	notifications.TypeAI:      "Asistente",
	notifications.TypeCommand: "Orden",
	notifications.TypeInfo:    "Aviso",
}

// Digest prepara los resúmenes por cultivo (cada tantas horas y el diario),
// revisados cada minuto dentro de la API (sin cron en el servidor).
type Digest struct {
	repo          CropRepository
	channels      *Service
	sender        *Sender
	crops         *crops.Service
	notifications notifications.Repository
	readings      *readings.Service
	commands      *commands.Service
	clock         func() time.Time
	zone          *time.Location
	webBaseURL    string
}

// NewDigest crea el preparador de resúmenes.
func NewDigest(repo CropRepository, channels *Service, sender *Sender, cropsSvc *crops.Service,
	notificationsRepo notifications.Repository, readingsSvc *readings.Service, commandsSvc *commands.Service,
	clock func() time.Time, zone *time.Location, webBaseURL string) *Digest {
	return &Digest{repo: repo, channels: channels, sender: sender, crops: cropsSvc, notifications: notificationsRepo,
		readings: readingsSvc, commands: commandsSvc, clock: clock, zone: zone, webBaseURL: strings.TrimRight(webBaseURL, "/")}
}

// Run revisa los resúmenes pendientes (ticker).
func (d *Digest) Run(ctx context.Context) {
	now := d.clock()
	enabled, err := d.repo.FindEnabled(ctx)
	if err != nil {
		slog.Warn("could not process digests", "err", err)
		return
	}
	for i := range enabled {
		if err := d.process(ctx, &enabled[i], now); err != nil {
			slog.Warn("could not prepare crop digest", "cropId", enabled[i].CropID.Hex(), "err", err)
		}
	}
}

func (d *Digest) process(ctx context.Context, settings *CropChannel, now time.Time) error {
	channel, ok := d.channels.AvailableChannel(settings.Type)
	if !ok {
		return nil
	}
	crop, err := d.crops.Find(ctx, settings.CropID.Hex())
	if err != nil || crop == nil {
		return err
	}
	changed := false
	if settings.Delivery == DeliveryDigest {
		hours := settings.DigestHours
		if hours < 1 {
			hours = 1
		}
		switch {
		case settings.LastDigestAt == nil:
			settings.LastDigestAt = &now
			changed = true
		case !now.Before(settings.LastDigestAt.Add(time.Duration(hours) * time.Hour)):
			items, err := d.notifications.FindByCropCreatedAfter(ctx, settings.CropID, *settings.LastDigestAt)
			if err != nil {
				return err
			}
			filtered := make([]notifications.Notification, 0, len(items))
			for _, item := range items {
				if containsType(settings.Events, item.Type) {
					filtered = append(filtered, item)
				}
			}
			if len(filtered) > 0 {
				d.send(ctx, channel, settings, crop, d.digest(crop, filtered, hours))
			}
			settings.LastDigestAt = &now
			changed = true
		}
	}
	if dueAt := d.dueToday(settings.DailySummaryAt, now); dueAt != nil && !now.Before(*dueAt) &&
		(settings.LastSummaryAt == nil || settings.LastSummaryAt.Before(*dueAt)) {
		msg, err := d.summary(ctx, crop, now)
		if err != nil {
			return err
		}
		d.send(ctx, channel, settings, crop, msg)
		settings.LastSummaryAt = &now
		changed = true
	}
	if changed {
		return d.repo.Save(ctx, settings)
	}
	return nil
}

func (d *Digest) send(ctx context.Context, channel NotificationChannel, settings *CropChannel, crop *crops.Crop, message ChannelMessage) {
	if link, _ := d.channels.Link(ctx, crop.OwnerID.Hex(), settings.Type); link != nil && link.Enabled {
		d.sender.ToOwner(ctx, channel, link, message)
	}
	d.sender.ToRecipients(ctx, channel, settings, message)
}

func (d *Digest) dueToday(at string, now time.Time) *time.Time {
	if strings.TrimSpace(at) == "" {
		return nil
	}
	parsed, err := time.ParseInLocation("15:04", at, d.zone)
	if err != nil {
		return nil
	}
	local := now.In(d.zone)
	due := time.Date(local.Year(), local.Month(), local.Day(), parsed.Hour(), parsed.Minute(), 0, 0, d.zone)
	return &due
}

func (d *Digest) digest(crop *crops.Crop, items []notifications.Notification, hours int) ChannelMessage {
	window := "the last " + fmt.Sprintf("%d", hours) + " hours"
	if hours == 1 {
		window = "the last hour"
	}
	lines := []string{fmt.Sprintf("%d %s en %s:", len(items), plural(len(items), "aviso", "avisos"), window)}
	limit := len(items)
	if limit > digestLines {
		limit = digestLines
	}
	for _, item := range items[:limit] {
		lines = append(lines, "• "+item.CreatedAt.In(d.zone).Format("15:04")+" "+digestKinds[item.Type]+": "+item.Title)
	}
	if len(items) > digestLines {
		lines = append(lines, fmt.Sprintf("… and %d more in the app.", len(items)-digestLines))
	}
	return ChannelMessage{Type: notifications.TypeInfo, Title: "Digest for «" + crop.Name + "»",
		Body: strings.Join(lines, "\n"), URL: d.url(crop)}
}

func (d *Digest) summary(ctx context.Context, crop *crops.Crop, now time.Time) (ChannelMessage, error) {
	dayAgo := now.Add(-24 * time.Hour)
	lines := []string{}
	if crop.Health == nil {
		lines = append(lines, "Health: not evaluated yet")
	} else {
		lines = append(lines, fmt.Sprintf("Salud: %s (%d/100)", crop.Health.Label, int(math.Round(crop.Health.Index))))
	}
	if crop.Device != nil && crop.Device.Online {
		lines = append(lines, "Connection: online")
	} else {
		lines = append(lines, "Connection: offline")
	}
	if latest, err := d.readings.Latest(ctx, crop.HexID()); err == nil && latest != nil {
		lines = append(lines, describe(latest.Measures))
	}
	oid, _ := objectid.Parse(crop.HexID())
	alerts, err := d.notifications.CountByCropTypeCreatedAfter(ctx, oid, notifications.TypeAlert, dayAgo)
	if err != nil {
		return ChannelMessage{}, err
	}
	orders, err := d.commands.CountSince(ctx, []string{crop.HexID()}, dayAgo)
	if err != nil {
		return ChannelMessage{}, err
	}
	lines = append(lines, fmt.Sprintf("Last 24 h: %d %s and %d %s", alerts, plural(int(alerts), "alert", "alerts"),
		orders, plural(int(orders), "command", "commands")))
	return ChannelMessage{Type: notifications.TypeInfo, Title: "Daily summary for «" + crop.Name + "»",
		Body: strings.Join(lines, "\n"), URL: d.url(crop)}, nil
}

func (d *Digest) url(crop *crops.Crop) string {
	return d.webBaseURL + "/app/crops/" + crop.HexID()
}

func describe(m readings.Measures) string {
	var parts []string
	if m.Temperature != nil {
		parts = append(parts, strings.Replace(fmt.Sprintf("%.1f °C", *m.Temperature), ".", ",", 1))
	}
	if m.Humidity != nil {
		parts = append(parts, fmt.Sprintf("humedad %d %%", int(math.Round(*m.Humidity))))
	}
	if m.SoilMoisture != nil {
		parts = append(parts, fmt.Sprintf("sustrato %d %%", int(math.Round(*m.SoilMoisture))))
	}
	if m.Brightness != nil {
		parts = append(parts, fmt.Sprintf("luz %d lux", int(math.Round(*m.Brightness))))
	}
	if len(parts) == 0 {
		return "Latest reading: no data"
	}
	return "Latest reading: " + strings.Join(parts, " · ")
}

func plural(n int, one, many string) string {
	if n == 1 {
		return one
	}
	return many
}
