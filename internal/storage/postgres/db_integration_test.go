//go:build integration

package postgres

import (
	"context"
	"errors"
	"os"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/actuators"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/commands"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// integrationPool se conecta a la base de pruebas indicada por
// SMARTPOT_TEST_DATABASE_URL. Sin esa variable, la prueba se salta: CI no
// necesita TimescaleDB para correr la suite normal.
func integrationPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	url := os.Getenv("SMARTPOT_TEST_DATABASE_URL")
	if url == "" {
		t.Skip("SMARTPOT_TEST_DATABASE_URL is not set")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, url)
	if err != nil {
		t.Fatalf("connect: %v", err)
	}
	t.Cleanup(pool.Close)
	if err := pool.Ping(ctx); err != nil {
		t.Fatalf("ping: %v", err)
	}
	return pool
}

func seedUser(t *testing.T, pool *pgxpool.Pool) primitive.ObjectID {
	t.Helper()
	id := primitive.NewObjectID()
	now := time.Now().UTC()
	_, err := pool.Exec(context.Background(),
		"INSERT INTO users (id, name, last_name, email, password_hash, role, created_at, updated_at) "+
			"VALUES ($1, 'Integration', 'Test', $2, 'x', 'USER', $3, $3)",
		id.Hex(), "integration-"+id.Hex()+"@example.test", now)
	if err != nil {
		t.Fatalf("insert user: %v", err)
	}
	t.Cleanup(func() { _, _ = pool.Exec(context.Background(), "DELETE FROM users WHERE id = $1", id.Hex()) })
	return id
}

func seedCrop(t *testing.T, pool *pgxpool.Pool, ownerID primitive.ObjectID, kind crops.CropKind) *crops.Crop {
	t.Helper()
	now := time.Now().UTC()
	crop := &crops.Crop{ID: primitive.NewObjectID(), OwnerID: ownerID, Name: "Integration crop",
		Type: crops.TypeLettuce, Kind: kind, Form: crops.FormPot, CreatedAt: now, UpdatedAt: now}
	if err := NewCropRepository(pool).Insert(context.Background(), crop); err != nil {
		t.Fatalf("insert crop: %v", err)
	}
	t.Cleanup(func() {
		_, _ = pool.Exec(context.Background(), "DELETE FROM crops WHERE id = $1", crop.ID.Hex())
	})
	return crop
}

func seedActuator(t *testing.T, pool *pgxpool.Pool, cropID primitive.ObjectID) *actuators.Actuator {
	t.Helper()
	actuator := &actuators.Actuator{ID: primitive.NewObjectID(), CropID: cropID, Type: actuators.WaterPump,
		CreatedAt: time.Now().UTC()}
	if err := NewActuatorRepository(pool).Insert(context.Background(), actuator); err != nil {
		t.Fatalf("insert actuator: %v", err)
	}
	return actuator
}

func TestIntegrationCommandTransitionsAndInFlightIndex(t *testing.T) {
	pool := integrationPool(t)
	ctx := context.Background()
	repo := NewCommandRepository(pool)
	crop := seedCrop(t, pool, seedUser(t, pool), crops.KindReal)
	actuator := seedActuator(t, pool, crop.ID)
	now := time.Now().UTC()

	first := &commands.Command{ID: primitive.NewObjectID(), CropID: crop.ID, ActuatorID: actuator.ID,
		ActuatorType: actuators.WaterPump, Action: commands.Activate, Status: commands.StatusPending,
		Source: commands.SourceUser, CreatedAt: now.Add(-10 * time.Minute)}
	if err := repo.Insert(ctx, first); err != nil {
		t.Fatalf("insert first command: %v", err)
	}
	second := *first
	second.ID = primitive.NewObjectID()
	if err := repo.Insert(ctx, &second); !errors.Is(err, commands.ErrInFlight) {
		t.Fatalf("second in-flight command must map to ErrInFlight, got %v", err)
	}

	stale, err := repo.FindStaleSent(ctx, now.Add(-5*time.Minute))
	if err != nil || len(stale) != 1 || stale[0].ID != first.ID {
		t.Fatalf("FindStaleSent must include the old PENDING command: %v %+v", err, stale)
	}

	won, err := repo.MarkSent(ctx, first.ID, now)
	if err != nil || !won {
		t.Fatalf("MarkSent: won=%v err=%v", won, err)
	}
	if won, _ := repo.MarkSent(ctx, first.ID, now); won {
		t.Fatal("MarkSent must not win twice")
	}

	done, won, err := repo.Complete(ctx, first.ID, commands.StatusExecuted, "ok", now)
	if err != nil || !won || done.Status != commands.StatusExecuted || done.SentAt == nil {
		t.Fatalf("Complete: %+v won=%v err=%v", done, won, err)
	}
	if _, won, _ := repo.Complete(ctx, first.ID, commands.StatusFailed, "late", now); won {
		t.Fatal("a terminal command must not be completed again")
	}
	if _, won, _ := repo.Expire(ctx, first.ID, "late", now); won {
		t.Fatal("a terminal command must not expire")
	}
}

func TestIntegrationExpireReleasesPendingCommand(t *testing.T) {
	pool := integrationPool(t)
	ctx := context.Background()
	repo := NewCommandRepository(pool)
	crop := seedCrop(t, pool, seedUser(t, pool), crops.KindReal)
	actuator := seedActuator(t, pool, crop.ID)
	cmd := &commands.Command{ID: primitive.NewObjectID(), CropID: crop.ID, ActuatorID: actuator.ID,
		ActuatorType: actuators.WaterPump, Action: commands.Deactivate, Status: commands.StatusPending,
		Source: commands.SourceUser, CreatedAt: time.Now().UTC()}
	if err := repo.Insert(ctx, cmd); err != nil {
		t.Fatal(err)
	}
	expired, won, err := repo.Expire(ctx, cmd.ID, "timeout", time.Now().UTC())
	if err != nil || !won || expired.Status != commands.StatusExpired {
		t.Fatalf("Expire: %+v won=%v err=%v", expired, won, err)
	}
	inFlight, err := repo.ExistsInFlight(ctx, actuator.ID)
	if err != nil || inFlight {
		t.Fatalf("expired command must free the actuator: %v %v", inFlight, err)
	}
}

func TestIntegrationQuotaIsAtomic(t *testing.T) {
	pool := integrationPool(t)
	ctx := context.Background()
	repo := NewCropRepository(pool)

	owner := seedUser(t, pool)
	now := time.Now().UTC()
	makeCrop := func(kind crops.CropKind) *crops.Crop {
		return &crops.Crop{ID: primitive.NewObjectID(), OwnerID: owner, Name: "Quota",
			Type: crops.TypeLettuce, Kind: kind, Form: crops.FormPot, CreatedAt: now, UpdatedAt: now}
	}
	if err := repo.InsertWithQuota(ctx, makeCrop(crops.KindReal), 2, 1); err != nil {
		t.Fatalf("first crop: %v", err)
	}
	if err := repo.InsertWithQuota(ctx, makeCrop(crops.KindReal), 2, 1); err != nil {
		t.Fatalf("second crop: %v", err)
	}
	if err := repo.InsertWithQuota(ctx, makeCrop(crops.KindReal), 2, 1); !errors.Is(err, crops.ErrCropLimit) {
		t.Fatalf("third crop must hit the total quota, got %v", err)
	}

	virtualOwner := seedUser(t, pool)
	virtual := func() *crops.Crop {
		return &crops.Crop{ID: primitive.NewObjectID(), OwnerID: virtualOwner, Name: "Virtual",
			Type: crops.TypeLettuce, Kind: crops.KindVirtual, Form: crops.FormPot, CreatedAt: now, UpdatedAt: now}
	}
	if err := repo.InsertWithQuota(ctx, virtual(), 10, 1); err != nil {
		t.Fatalf("first virtual crop: %v", err)
	}
	if err := repo.InsertWithQuota(ctx, virtual(), 10, 1); !errors.Is(err, crops.ErrVirtualCropLimit) {
		t.Fatalf("second virtual crop must hit the virtual quota, got %v", err)
	}
	t.Cleanup(func() {
		_, _ = pool.Exec(ctx, "DELETE FROM crops WHERE owner_id = ANY($1)", []string{owner.Hex(), virtualOwner.Hex()})
	})
}

func TestIntegrationSaveAndDeviceKeyPreserveLiveDeviceState(t *testing.T) {
	pool := integrationPool(t)
	ctx := context.Background()
	repo := NewCropRepository(pool)
	now := time.Now().UTC()
	rotated := now.Add(-time.Hour)
	crop := &crops.Crop{ID: primitive.NewObjectID(), OwnerID: seedUser(t, pool), Name: "Device",
		Type: crops.TypeLettuce, Kind: crops.KindReal, Form: crops.FormPot,
		Device: &crops.Device{KeyCiphertext: "old-key", KeyRotatedAt: &rotated}, CreatedAt: now, UpdatedAt: now}
	if err := repo.Insert(ctx, crop); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _, _ = pool.Exec(ctx, "DELETE FROM crops WHERE id = $1", crop.ID.Hex()) })

	online := true
	if err := repo.MarkDeviceSeen(ctx, crop.ID, &online, now); err != nil {
		t.Fatal(err)
	}
	stale, err := repo.FindByID(ctx, crop.ID)
	if err != nil || stale == nil {
		t.Fatal(err)
	}
	stale.Name = "Renamed"
	stale.Device.Online = false // un PUT con estado viejo no debe pisar el JSONB device
	if err := repo.Save(ctx, stale); err != nil {
		t.Fatal(err)
	}
	afterSave, err := repo.FindByID(ctx, crop.ID)
	if err != nil || afterSave.Name != "Renamed" {
		t.Fatalf("editable columns must be saved: %+v %v", afterSave, err)
	}
	if afterSave.Device == nil || !afterSave.Device.Online {
		t.Fatal("Save must not overwrite device.online updated by MQTT")
	}
	if afterSave.Device.KeyCiphertext != "old-key" {
		t.Fatalf("Save must not overwrite the device key, got %q", afterSave.Device.KeyCiphertext)
	}

	if err := repo.UpdateDeviceKey(ctx, crop.ID, "new-key", now, now); err != nil {
		t.Fatal(err)
	}
	rotatedCrop, err := repo.FindByID(ctx, crop.ID)
	if err != nil || rotatedCrop.Device == nil || rotatedCrop.Device.KeyCiphertext != "new-key" ||
		rotatedCrop.Device.KeyRotatedAt == nil {
		t.Fatalf("UpdateDeviceKey: %+v %v", rotatedCrop, err)
	}
	if !rotatedCrop.Device.Online {
		t.Fatal("rotating the key must not reset online")
	}
}

func TestIntegrationLatestByCropIDsAndSeriesAverages(t *testing.T) {
	pool := integrationPool(t)
	ctx := context.Background()
	repo := NewReadingRepository(pool)
	owner := seedUser(t, pool)
	cropA := seedCrop(t, pool, owner, crops.KindReal)
	cropB := seedCrop(t, pool, owner, crops.KindReal)
	base := time.Now().UTC().Truncate(time.Minute).Add(-2 * time.Minute)

	value := func(v float64) *float64 { return &v }
	rows := []readings.Reading{
		{CropID: cropA.ID, MeasuredAt: base.Add(10 * time.Second), Source: readings.SourceMQTT,
			Measures: readings.Measures{Temperature: value(10)}},
		{CropID: cropA.ID, MeasuredAt: base.Add(20 * time.Second), Source: readings.SourceMQTT,
			Measures: readings.Measures{Temperature: value(30)}},
		{CropID: cropB.ID, MeasuredAt: base.Add(15 * time.Second), Source: readings.SourceMQTT,
			Measures: readings.Measures{Temperature: value(5)}},
	}
	for i := range rows {
		rows[i].ID = primitive.NewObjectID()
		if err := repo.Insert(ctx, &rows[i]); err != nil {
			t.Fatalf("insert reading: %v", err)
		}
	}

	latest, err := repo.LatestByCropIDs(ctx, []primitive.ObjectID{cropA.ID, cropB.ID})
	if err != nil {
		t.Fatal(err)
	}
	if got := latest[cropA.ID.Hex()]; got == nil || got.Measures.Temperature == nil || *got.Measures.Temperature != 30 {
		t.Fatalf("latest for A: %+v", got)
	}
	if got := latest[cropB.ID.Hex()]; got == nil || got.Measures.Temperature == nil || *got.Measures.Temperature != 5 {
		t.Fatalf("latest for B: %+v", got)
	}

	buckets, err := repo.SeriesAverages(ctx, []primitive.ObjectID{cropA.ID}, "temperature",
		base.Add(5*time.Second), 1)
	if err != nil {
		t.Fatal(err)
	}
	if len(buckets) != 1 || !buckets[0].Time.Equal(base) || buckets[0].Value != 20 {
		t.Fatalf("aligned weighted average: %+v", buckets)
	}
}
