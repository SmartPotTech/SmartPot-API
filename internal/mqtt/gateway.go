package mqtt

import (
	"crypto/rand"
	"encoding/hex"
	"hash/fnv"
	"log/slog"
	"runtime"
	"sync"
	"sync/atomic"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	paho "github.com/eclipse/paho.mqtt.golang"
)

const (
	publishTimeout     = 5 * time.Second
	connectTimeout     = 10 * time.Second
	defaultJobQueueCap = 2048
)

type mqttJob struct {
	topic   string
	payload string
}

// Gateway es el cliente del broker con reconexión automática y worker pool concurrente con backpressure.
// Evita la creación descontrolada de goroutines ante ráfagas de telemetría y soporta suscripciones compartidas ($share).
type Gateway struct {
	enabled     bool
	client      paho.Client
	topics      *TopicResolver
	router      func(topic, payload string)
	log         *slog.Logger
	connected   atomic.Bool
	sharedGroup string

	// Un worker por cola; cada cultivo cae siempre en la misma cola, así sus mensajes (estado, lecturas y ACK)
	// se procesan en orden aunque haya varios workers.
	shards     []chan mqttJob
	workerWg   sync.WaitGroup
	stopSignal chan struct{}
	stopped    atomic.Bool
}

// NewGateway crea el gateway. Si MQTT está deshabilitado, no abre conexión.
func NewGateway(cfg config.MQTT, topics *TopicResolver, logger *slog.Logger) *Gateway {
	g := &Gateway{
		enabled:     cfg.Enabled,
		topics:      topics,
		log:         logger,
		sharedGroup: cfg.SharedSubscriptionGroup,
		stopSignal:  make(chan struct{}),
	}
	if !cfg.Enabled {
		logger.Info("MQTT is disabled: the API will not receive telemetry or send commands")
		return g
	}

	opts := paho.NewClientOptions().
		AddBroker(cfg.BrokerURI).
		SetClientID(cfg.ClientID + "-" + randomSuffix()).
		SetCleanSession(true).
		SetAutoReconnect(true).
		SetConnectRetry(true).
		SetConnectRetryInterval(5 * time.Second).
		SetMaxReconnectInterval(30 * time.Second).
		SetKeepAlive(30 * time.Second).
		SetConnectTimeout(connectTimeout).
		SetOnConnectHandler(g.onConnected).
		SetConnectionLostHandler(func(_ paho.Client, err error) {
			g.connected.Store(false)
			logger.Warn("lost connection to the MQTT broker", "err", err)
		}).
		SetDefaultPublishHandler(g.onMessage)
	if cfg.Username != "" {
		opts.SetUsername(cfg.Username)
	}
	if cfg.Password != "" {
		opts.SetPassword(cfg.Password)
	}
	g.client = paho.NewClient(opts)
	return g
}

// SetRouter fija el enrutador de mensajes entrantes.
func (g *Gateway) SetRouter(router func(topic, payload string)) { g.router = router }

// Start arranca el worker pool e inicia la conexión al broker.
func (g *Gateway) Start() {
	if !g.enabled {
		return
	}

	// Inicia el pool de workers para procesar mensajes concurrentemente con backpressure
	numWorkers := runtime.NumCPU() * 2
	if numWorkers < 4 {
		numWorkers = 4
	}
	if numWorkers > 32 {
		numWorkers = 32
	}
	perShard := max(defaultJobQueueCap/numWorkers, 64)
	g.shards = make([]chan mqttJob, numWorkers)
	for i := range g.shards {
		g.shards[i] = make(chan mqttJob, perShard)
	}
	g.log.Info("starting MQTT worker pool", "workers", numWorkers, "queueCapacity", perShard*numWorkers)
	for i := range numWorkers {
		g.workerWg.Add(1)
		go g.worker(i)
	}

	g.client.Connect()
}

// Close deja de aceptar mensajes nuevos, drena por completo las colas de los
// shards y solo al final desconecta del broker. Así una ráfaga ya recibida no
// se pierde por apagar el socket antes de vaciar el worker pool.
func (g *Gateway) Close() {
	if !g.enabled {
		return
	}
	if g.stopped.Swap(true) {
		return
	}
	g.connected.Store(false)

	// Primero se cierra la compuerta de entrada y se vacían las colas.
	close(g.stopSignal)
	drained := make(chan struct{})
	go func() {
		g.workerWg.Wait()
		close(drained)
	}()
	select {
	case <-drained:
		g.log.Info("MQTT worker pool drained")
	case <-time.After(15 * time.Second):
		g.log.Warn("MQTT worker pool drain timed out; disconnecting anyway")
	}

	// La desconexión va al final: los mensajes que ya estaban en las colas se
	// procesaron antes de cerrar el socket.
	if g.client != nil && g.client.IsConnected() {
		g.client.Disconnect(3000)
	}
}

// IsEnabled indica si MQTT está habilitado.
func (g *Gateway) IsEnabled() bool { return g.enabled }

// IsConnected indica si hay conexión con el broker y las suscripciones ya
// recibieron el SUBACK; /health no reporta UP antes de estar operativo.
func (g *Gateway) IsConnected() bool {
	return g.enabled && g.connected.Load()
}

// Publish publica un mensaje; devuelve false si el broker no lo aceptó.
func (g *Gateway) Publish(topic, payload string, qos byte, retained bool) bool {
	if g.stopped.Load() || g.client == nil || !g.client.IsConnected() {
		return false
	}
	token := g.client.Publish(topic, qos, retained, payload)
	if !token.WaitTimeout(publishTimeout) {
		g.log.Warn("timed out publishing MQTT message", "topic", topic)
		return false
	}
	if err := token.Error(); err != nil {
		g.log.Warn("could not publish MQTT message", "topic", topic, "err", err)
		return false
	}
	return true
}

// PublishCommand publica una orden en el tópico de comandos del cultivo (QoS 1).
func (g *Gateway) PublishCommand(cropID, payload string) bool {
	return g.Publish(g.topics.Commands(cropID), payload, 1, false)
}

func (g *Gateway) onConnected(c paho.Client) {
	subscriptions := make(map[string]byte)
	topicsList := g.topics.Subscriptions()
	if g.sharedGroup != "" {
		topicsList = g.topics.SharedSubscriptions(g.sharedGroup)
	}
	for _, topic := range topicsList {
		subscriptions[topic] = 1
	}

	// El SUBACK es la prueba de que la API ya recibe telemetría: solo entonces
	// se marca connected. Si el broker aún no está listo, se reintenta con
	// backoff en vez de dejar la API sorda hasta la próxima reconexión.
	delay := time.Second
	for attempt := 0; attempt < 6; attempt++ {
		token := c.SubscribeMultiple(subscriptions, g.onMessage)
		if token.WaitTimeout(connectTimeout) && token.Error() == nil {
			g.connected.Store(true)
			g.log.Info("connected to the MQTT broker", "subscribed", len(subscriptions),
				"sharedGroup", g.sharedGroup)
			return
		}
		if !c.IsConnected() || g.stopped.Load() {
			return
		}
		g.log.Warn("MQTT subscription failed; retrying", "attempt", attempt+1, "retryIn", delay.String())
		select {
		case <-time.After(delay):
		case <-g.stopSignal:
			return
		}
		delay *= 2
		if delay > 16*time.Second {
			delay = 16 * time.Second
		}
	}
	g.connected.Store(false)
	g.log.Error("could not subscribe to SmartPot MQTT topics after retries")
}

func (g *Gateway) onMessage(_ paho.Client, m paho.Message) {
	if g.router == nil || g.stopped.Load() {
		return
	}
	// Los mensajes retenidos de telemetría y ACK son historia: reinsertarlos
	// duplicaría lecturas y cerraría órdenes viejas (M-02). El estado retenido
	// (LWT) sí se procesa porque representa la última conexión conocida.
	if m.Retained() {
		if parsed, ok := g.topics.Parse(m.Topic()); ok {
			switch parsed.Kind {
			case KindTelemetry, KindCommandAck:
				g.log.Debug("retained telemetry or ACK ignored", "topic", m.Topic())
				return
			}
		}
	}
	job := mqttJob{topic: m.Topic(), payload: string(m.Payload())}
	// Si la cola del cultivo está llena se espera: el cliente deja de leer del socket y el broker retiene los
	// mensajes (presión real) en vez de perder lecturas o ACK.
	select {
	case g.shards[g.shardFor(job.topic)] <- job:
	case <-g.stopSignal:
	}
}

// shardFor elige la cola del cultivo del tópico (FNV-1a sobre su id).
func (g *Gateway) shardFor(topic string) int {
	key := topic
	if parsed, ok := g.topics.Parse(topic); ok {
		key = parsed.CropID
	}
	hash := fnv.New32a()
	_, _ = hash.Write([]byte(key))
	return int(hash.Sum32() % uint32(len(g.shards)))
}

func (g *Gateway) worker(id int) {
	defer g.workerWg.Done()
	for {
		select {
		case <-g.stopSignal:
			// Drena los mensajes restantes en la cola antes de salir
			for {
				select {
				case job := <-g.shards[id]:
					if g.router != nil {
						g.router(job.topic, job.payload)
					}
				default:
					return
				}
			}
		case job, ok := <-g.shards[id]:
			if !ok {
				return
			}
			if g.router != nil {
				g.router(job.topic, job.payload)
			}
		}
	}
}

func randomSuffix() string {
	b := make([]byte, 4)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}
