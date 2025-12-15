package dev.starq.picassolve.service;

import dev.starq.picassolve.dto.ScoreBoardEntry;
import dev.starq.picassolve.entity.ScoreSnapshot;
import dev.starq.picassolve.entity.ScoreSnapshot.SnapshotPeriod;
import dev.starq.picassolve.repository.ScoreSnapshotRepository;
import dev.starq.picassolve.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingQueryService {

    private final UserRepository userRepository;
    private final ScoreSnapshotRepository snapshotRepository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public enum RankingPeriod {
        LIVE, DAILY, WEEKLY, MONTHLY;

        public static RankingPeriod from(String raw) {
            if (raw == null) return LIVE;
            try {
                return RankingPeriod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return LIVE;
            }
        }
    }

    public List<ScoreBoardEntry> getRanking(String rawPeriod) {
        RankingPeriod period = RankingPeriod.from(rawPeriod);
        return switch (period) {
            case LIVE -> liveRanking();
            case DAILY -> latestSnapshotRanking(SnapshotPeriod.DAILY);
            case WEEKLY -> latestSnapshotRanking(SnapshotPeriod.WEEKLY);
            case MONTHLY -> monthlyAggregateRanking();
        };
    }

    private List<ScoreBoardEntry> liveRanking() {
        return userRepository.findAll().stream()
            .filter(u -> u.getScore() > 0)
            .sorted(Comparator.comparingInt((dev.starq.picassolve.entity.User u) -> u.getScore()).reversed())
            .map(u -> new ScoreBoardEntry(u.getName(), u.getTeam(), u.getScore()))
            .toList();
    }

    private List<ScoreBoardEntry> latestSnapshotRanking(SnapshotPeriod period) {
        LocalDate latest = snapshotRepository.findTopByPeriodOrderBySnapshotDateDesc(period)
            .map(ScoreSnapshot::getSnapshotDate)
            .orElse(null);
        if (latest == null) return List.of();

        return snapshotRepository.findByPeriodAndSnapshotDate(period, latest).stream()
            .filter(s -> s.getScore() > 0)
            .sorted(Comparator.comparingInt(ScoreSnapshot::getScore).reversed())
            .map(s -> new ScoreBoardEntry(s.getUsername(), s.getTeam(), s.getScore()))
            .toList();
    }

    /**
        * 월간 랭킹: 이번 달 일간 스냅샷 합산.
        * 주간 리셋 후에도 월간 누적이 유지되도록 날짜 범위를 모읍니다.
        */
    private List<ScoreBoardEntry> monthlyAggregateRanking() {
        LocalDate today = LocalDate.now(KST);
        LocalDate monthStart = today.withDayOfMonth(1);

        Map<UUID, ScoreBoardEntry> acc = new LinkedHashMap<>();
        snapshotRepository.findByPeriodAndSnapshotDateBetween(SnapshotPeriod.DAILY, monthStart, today)
            .forEach(s -> {
                ScoreBoardEntry entry = acc.computeIfAbsent(s.getUserId(),
                    id -> new ScoreBoardEntry(s.getUsername(), s.getTeam(), 0));
                entry.setScore(entry.getScore() + s.getScore());
            });

        return acc.values().stream()
            .filter(e -> e.getScore() > 0)
            .sorted(Comparator.comparingInt(ScoreBoardEntry::getScore).reversed())
            .toList();
    }
}
