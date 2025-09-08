package dev.starq.picassolve.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScoreBoardEntry {
    private String name;
    private int team;
    private int score;
}
