package com.unihub.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramWebhookRequest(
        @JsonProperty("update_id") Long updateId,
        Message message
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("message_id") Long messageId,
            From from,
            Chat chat,
            Long date,
            String text
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record From(
            Long id,
            @JsonProperty("is_bot") Boolean isBot,
            @JsonProperty("first_name") String firstName
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(
            Long id,
            String type
    ) {
    }
}
