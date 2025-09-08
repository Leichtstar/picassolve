package dev.starq.picassolve.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="words")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Word {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=100)
    private String text;
}
