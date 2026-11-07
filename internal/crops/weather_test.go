package crops

import (
	"context"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/virtualdevices"
)

type fakeWeatherSource struct {
	available bool
	weather   *virtualdevices.Weather
	ok        bool
	delay     time.Duration
	calls     int
	latitude  float64
	longitude float64
}

func (f *fakeWeatherSource) IsAvailable() bool { return f.available }

func (f *fakeWeatherSource) Weather(ctx context.Context, latitude, longitude float64) (*virtualdevices.Weather, bool) {
	f.calls++
	f.latitude, f.longitude = latitude, longitude
	if f.delay > 0 {
		select {
		case <-time.After(f.delay):
		case <-ctx.Done():
			return nil, false
		}
	}
	return f.weather, f.ok
}

func weatherCrop() *Crop {
	return &Crop{Placement: &geo.Placement{Location: &geo.Location{
		Name: "Bogota", Latitude: 4.7110, Longitude: -74.0721,
	}}}
}

func TestWeatherCurrentRequiresPlacement(t *testing.T) {
	source := &fakeWeatherSource{available: true, ok: true, weather: &virtualdevices.Weather{Temperature: 20}}
	svc := NewWeatherService(source, cache.NewStore(nil))

	for _, crop := range []*Crop{{}, {Placement: &geo.Placement{}}} {
		if weather, ok := svc.Current(context.Background(), crop); ok || weather != nil {
			t.Fatalf("a crop without location must not have weather: %+v", weather)
		}
	}
	if source.calls != 0 {
		t.Fatalf("the source must not be queried without location: %d calls", source.calls)
	}
}

func TestWeatherCurrentRequiresAvailableSource(t *testing.T) {
	source := &fakeWeatherSource{available: false, ok: true, weather: &virtualdevices.Weather{Temperature: 20}}
	svc := NewWeatherService(source, cache.NewStore(nil))

	if _, ok := svc.Current(context.Background(), weatherCrop()); ok {
		t.Fatal("without an available source there is no weather")
	}
	if source.calls != 0 {
		t.Fatalf("the source must not be queried when disabled: %d calls", source.calls)
	}
}

func TestWeatherCurrentCachesSuccess(t *testing.T) {
	expected := &virtualdevices.Weather{Temperature: 18.5, Humidity: 70, Condition: "CLOUDY"}
	source := &fakeWeatherSource{available: true, ok: true, weather: expected}
	svc := NewWeatherService(source, cache.NewStore(nil))
	crop := weatherCrop()

	first, ok := svc.Current(context.Background(), crop)
	if !ok || first == nil || first.Temperature != 18.5 {
		t.Fatalf("unexpected weather: %+v ok=%v", first, ok)
	}
	second, ok := svc.Current(context.Background(), crop)
	if !ok || second == nil || second.Condition != "CLOUDY" {
		t.Fatalf("unexpected cached weather: %+v ok=%v", second, ok)
	}
	if source.calls != 1 {
		t.Fatalf("the second query must come from the cache: %d calls", source.calls)
	}
	if source.latitude != 4.711 || source.longitude != -74.0721 {
		t.Fatalf("unexpected coordinates: %f %f", source.latitude, source.longitude)
	}
}

func TestWeatherCurrentCachesFailure(t *testing.T) {
	source := &fakeWeatherSource{available: true, ok: false}
	svc := NewWeatherService(source, cache.NewStore(nil))
	crop := weatherCrop()

	for i := 0; i < 2; i++ {
		if weather, ok := svc.Current(context.Background(), crop); ok || weather != nil {
			t.Fatalf("a failed lookup must not return weather: %+v", weather)
		}
	}
	if source.calls != 1 {
		t.Fatalf("a failure must be cached during the retry window: %d calls", source.calls)
	}
}

func TestWeatherCurrentHandlesSourceTimeout(t *testing.T) {
	source := &fakeWeatherSource{available: true, ok: true, weather: &virtualdevices.Weather{Temperature: 20}, delay: 200 * time.Millisecond}
	svc := NewWeatherService(source, cache.NewStore(nil))

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Millisecond)
	defer cancel()
	start := time.Now()
	weather, ok := svc.Current(ctx, weatherCrop())
	if ok || weather != nil {
		t.Fatalf("a timed out source must not return weather: %+v", weather)
	}
	if elapsed := time.Since(start); elapsed > time.Second {
		t.Fatalf("the timeout was not respected: %v", elapsed)
	}
}

func TestWeatherCurrentHandlesCorruptCache(t *testing.T) {
	source := &fakeWeatherSource{available: true, ok: true, weather: &virtualdevices.Weather{Temperature: 20}}
	store := cache.NewStore(nil)
	store.Put("weather:4.71:-74.07", "{not-json", time.Minute)
	svc := NewWeatherService(source, store)

	if weather, ok := svc.Current(context.Background(), weatherCrop()); ok || weather != nil {
		t.Fatalf("a corrupt cache entry must not return weather: %+v", weather)
	}
	if source.calls != 0 {
		t.Fatalf("a corrupt cache entry must not fall through to the source: %d calls", source.calls)
	}
}

func TestWeatherCurrentDifferentLocationsAreCachedSeparately(t *testing.T) {
	source := &fakeWeatherSource{available: true, ok: true, weather: &virtualdevices.Weather{Temperature: 20}}
	svc := NewWeatherService(source, cache.NewStore(nil))

	other := &Crop{Placement: &geo.Placement{Location: &geo.Location{Name: "Madrid", Latitude: 40.4168, Longitude: -3.7038}}}
	if _, ok := svc.Current(context.Background(), weatherCrop()); !ok {
		t.Fatal("first location must resolve")
	}
	if _, ok := svc.Current(context.Background(), other); !ok {
		t.Fatal("second location must resolve")
	}
	if source.calls != 2 {
		t.Fatalf("different locations must query separately: %d calls", source.calls)
	}
}
