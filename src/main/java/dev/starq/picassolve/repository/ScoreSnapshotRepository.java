package dev.starq.picassolve.repository;

import dev.starq.picassolve.entity.ScoreSnapshot;
import dev.starq.picassolve.entity.ScoreSnapshot.SnapshotPeriod;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;

public interface ScoreSnapshotRepository extends JpaRepository<ScoreSnapshot, UUID> {
    @Modifying
    @Query("delete from ScoreSnapshot s where s.snapshotDate = :snapshotDate and s.period = :period")
    void deleteBySnapshotDateAndPeriod(LocalDate snapshotDate, SnapshotPeriod period);

    Optional<ScoreSnapshot> findTopByPeriodOrderBySnapshotDateDesc(SnapshotPeriod period);

    List<ScoreSnapshot> findByPeriodAndSnapshotDate(SnapshotPeriod period, LocalDate snapshotDate);

    List<ScoreSnapshot> findByPeriodAndSnapshotDateBetween(
            SnapshotPeriod period,
            LocalDate start,
            LocalDate end);
}
