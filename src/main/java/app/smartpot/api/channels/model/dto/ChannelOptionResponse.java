package app.smartpot.api.channels.model.dto;

import app.smartpot.api.channels.model.entity.ChannelType;

import java.util.List;

/**
 * Un canal que ofrece SmartPot y, si existe, el vínculo de la persona con él. Si el servidor no lo tiene
 * configurado, available es false y requirements dice qué variables faltan.
 */
public record ChannelOptionResponse(ChannelType type, String name, String description, boolean available,
                                    String handle, List<String> requirements, ChannelLinkResponse link) {
}
