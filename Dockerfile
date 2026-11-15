# Etapa de compilación: aquí se arma el binario estático.
FROM golang:1.27.4-alpine AS build

WORKDIR /src

# Actualización de paquetes de seguridad en Alpine y certificados CA para el runtime scratch
RUN apk update && apk upgrade --no-cache && apk add --no-cache ca-certificates

# Descarga de módulos con caché de BuildKit: los rebuilds no vuelven a bajar dependencias de la red.
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download

COPY . .
# CGO desactivado + tzdata incrustada en el binario: no depende de libc ni de
# /usr/share/zoneinfo, así la imagen de ejecución puede ser "scratch". Las cachés de módulos y de compilación
# aceleran los rebuilds sin dejar rastro en la imagen final.
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux go build -trimpath -tags timetzdata -ldflags="-s -w" -o /out/smartpot-api ./cmd/api

# Etapa de ejecución: scratch, sin shell, sin gestor de paquetes y sin
# herramientas de red. Un contenedor comprometido no encuentra utilidades que
# aprovechar ni forma de escalar.
FROM scratch

LABEL org.opencontainers.image.title="SmartPot API" \
      org.opencontainers.image.description="API REST y MQTT de SmartPot (Go)" \
      org.opencontainers.image.source="https://github.com/SmartPotTech/SmartPot-API-Go" \
      org.opencontainers.image.licenses="MIT"

# Solo el bundle de certificados CA (para las conexiones TLS salientes) y el binario.
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /out/smartpot-api /smartpot-api

ENV PORT=8091 \
    TZ=UTC

# Usuario no root (numérico: scratch no tiene /etc/passwd).
USER 1000:1000

EXPOSE 8091

# El propio binario hace el chequeo; no hace falta wget ni curl en la imagen.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD ["/smartpot-api", "healthcheck"]

ENTRYPOINT ["/smartpot-api"]
