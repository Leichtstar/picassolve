package dev.starq.picassolve.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=50)
    private String name;

    @Column(nullable=false)
    private int team;

    @Column(nullable=false)
    private int score;

    @Enumerated(EnumType.STRING)
    private Role role;

    public enum Role { PARTICIPANT, DRAWER, ADMIN }
}