package app.smartpot.api.channels.model.dto;

import app.smartpot.api.channels.model.entity.ChannelType;

import java.time.Instant;

/** Código de un solo uso para vincular el canal; url lo abre directamente (por ejemplo, t.me/bot?start=...). */
public record LinkCodeResponse(ChannelType type, String code, String url, Instant expiresAt) {
}
