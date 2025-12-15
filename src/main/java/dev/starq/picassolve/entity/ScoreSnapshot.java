package dev.starq.picassolve.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일간/주간/월간 랭킹 스냅샷을 보관한다.
 */
@Entity
@Table(name = "score_snapshots")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoreSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private int team;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SnapshotPeriod period;

    @Column(nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum SnapshotPeriod {
        DAILY,
        WEEKLY,
        MONTHLY
    }
}
