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
		logger.Info("MQTT deshabilitado: la API no recibirá telemetría ni enviará comandos")
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
			logger.Warn("se perdió la conexión con el broker MQTT", "err", err)
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
	g.log.Info("iniciando worker pool de MQTT", "workers", numWorkers, "queueCapacity", perShard*numWorkers)
	for i := range numWorkers {
		g.workerWg.Add(1)
		go g.worker(i)
	}

	g.client.Connect()
}

// Close drena los mensajes pendientes en el worker pool y cierra la conexión al broker.
func (g *Gateway) Close() {
	if !g.enabled {
		return
	}
	if g.stopped.Swap(true) {
		return
	}

	// Desconecta del broker para dejar de recibir mensajes nuevos
	if g.client != nil && g.client.IsConnected() {
		g.client.Disconnect(3000)
	}

	// Señaliza a los workers y espera el drenado limpio de la cola
	close(g.stopSignal)
	g.workerWg.Wait()
	g.log.Info("worker pool de MQTT detenido limpiamente")
}

// IsEnabled indica si MQTT está habilitado.
func (g *Gateway) IsEnabled() bool { return g.enabled }

// IsConnected indica si hay conexión con el broker.
func (g *Gateway) IsConnected() bool {
	return g.enabled && g.client != nil && g.client.IsConnected()
}

// Publish publica un mensaje; devuelve false si el broker no lo aceptó.
func (g *Gateway) Publish(topic, payload string, qos byte, retained bool) bool {
	if !g.IsConnected() {
		return false
	}
	token := g.client.Publish(topic, qos, retained, payload)
	if !token.WaitTimeout(publishTimeout) {
		g.log.Warn("tiempo de espera agotado al publicar", "topic", topic)
		return false
	}
	if err := token.Error(); err != nil {
		g.log.Warn("no se pudo publicar", "topic", topic, "err", err)
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
	token := c.SubscribeMultiple(subscriptions, g.onMessage)
	if token.WaitTimeout(connectTimeout) && token.Error() != nil {
		g.log.Error("no se pudo suscribir a los tópicos de SmartPot", "err", token.Error())
	}
	reconnect := g.connected.Swap(true)
	g.log.Info("conectado al broker MQTT", "reconexion", reconnect, "sharedGroup", g.sharedGroup)
}

func (g *Gateway) onMessage(_ paho.Client, m paho.Message) {
	if g.router == nil || g.stopped.Load() {
		return
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
