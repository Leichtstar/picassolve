package dev.starq.picassolve.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMessage {
    private String from;
    private String text;
    private boolean system;
}
