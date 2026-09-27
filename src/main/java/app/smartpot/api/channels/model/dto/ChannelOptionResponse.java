package app.smartpot.api.channels.model.dto;

import app.smartpot.api.channels.model.entity.ChannelType;

/** Un canal que ofrece el servidor y, si existe, el vínculo de la persona con él. */
public record ChannelOptionResponse(ChannelType type, String name, boolean available, String handle,
                                    ChannelLinkResponse link) {
}
