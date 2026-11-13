// Package events es un bus de eventos en proceso que reemplaza al
// ApplicationEventPublisher de Spring. Los manejadores se suscriben por tipo de
// evento y se invocan de forma síncrona, como los @EventListener por defecto.
package events

import (
	"fmt"
	"log/slog"
	"reflect"
	"runtime/debug"
	"sync"
)

// Bus despacha eventos a los manejadores suscritos a su tipo.
type Bus struct {
	mu       sync.RWMutex
	handlers map[reflect.Type][]func(any)
}

// NewBus crea un bus vacío.
func NewBus() *Bus {
	return &Bus{handlers: make(map[reflect.Type][]func(any))}
}

// Publish entrega el evento a los manejadores de su tipo, en orden de registro.
// Cada manejador se aísla con recover para que un pánico no derribe el proceso ni bloquee al resto.
func (b *Bus) Publish(event any) {
	b.mu.RLock()
	var handlers []func(any)
	handlers = append(handlers, b.handlers[reflect.TypeOf(event)]...)
	b.mu.RUnlock()
	for _, h := range handlers {
		invokeSafely(h, event)
	}
}

func invokeSafely(h func(any), event any) {
	defer func() {
		if r := recover(); r != nil {
			slog.Error("event handler panic",
				"eventType", fmt.Sprintf("%T", event),
				"panic", r,
				"stack", string(debug.Stack()),
			)
		}
	}()
	h(event)
}

func (b *Bus) subscribe(t reflect.Type, h func(any)) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.handlers[t] = append(b.handlers[t], h)
}

// Subscribe registra un manejador tipado para los eventos de tipo T.
func Subscribe[T any](b *Bus, handler func(T)) {
	var zero T
	b.subscribe(reflect.TypeOf(zero), func(e any) {
		handler(e.(T))
	})
}
