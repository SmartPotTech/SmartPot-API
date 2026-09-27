package app.smartpot.api.channels.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Lo que interesa de una novedad del Bot API de Telegram: un mensaje de texto en un chat. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(@JsonProperty("update_id") long updateId, Message message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(@JsonProperty("message_id") long messageId, Chat chat, User from, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id, String type, String username, @JsonProperty("first_name") String firstName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(long id, @JsonProperty("first_name") String firstName, String username) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Updates(boolean ok, List<TelegramUpdate> result) {
    }
}
