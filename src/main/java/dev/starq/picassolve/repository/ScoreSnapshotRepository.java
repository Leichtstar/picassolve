package dev.starq.picassolve.repository;

import dev.starq.picassolve.entity.ScoreSnapshot;
import dev.starq.picassolve.entity.ScoreSnapshot.SnapshotPeriod;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScoreSnapshotRepository extends JpaRepository<ScoreSnapshot, Long> {
    void deleteBySnapshotDateAndPeriod(LocalDate snapshotDate, SnapshotPeriod period);

    Optional<ScoreSnapshot> findTopByPeriodOrderBySnapshotDateDesc(SnapshotPeriod period);

    List<ScoreSnapshot> findByPeriodAndSnapshotDate(SnapshotPeriod period, LocalDate snapshotDate);

    List<ScoreSnapshot> findByPeriodAndSnapshotDateBetween(
        SnapshotPeriod period,
        LocalDate start,
        LocalDate end
    );
}
