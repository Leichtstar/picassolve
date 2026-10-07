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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingQueryService {

    private final UserRepository userRepository;
    private final ScoreSnapshotRepository snapshotRepository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_LIMIT = 50;

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
        return getRanking(rawPeriod, DEFAULT_LIMIT);
    }

    public List<ScoreBoardEntry> getRanking(String rawPeriod, int limit) {
        int capped = Math.min(Math.max(limit, 1), 100);
        RankingPeriod period = RankingPeriod.from(rawPeriod);
        return switch (period) {
            case LIVE -> liveRanking(capped);
            case DAILY -> latestSnapshotRanking(SnapshotPeriod.DAILY, capped);
            case WEEKLY -> latestSnapshotRanking(SnapshotPeriod.WEEKLY, capped);
            case MONTHLY -> monthlyAggregateRanking(capped);
        };
    }

    private List<ScoreBoardEntry> liveRanking(int limit) {
        return userRepository.findTopByScore(PageRequest.of(0, limit)).stream()
            .map(u -> new ScoreBoardEntry(u.getName(), u.getTeam(), u.getScore()))
            .toList();
    }

    private List<ScoreBoardEntry> latestSnapshotRanking(SnapshotPeriod period, int limit) {
        LocalDate latest = snapshotRepository.findTopByPeriodOrderBySnapshotDateDesc(period)
            .map(ScoreSnapshot::getSnapshotDate)
            .orElse(null);
        if (latest == null) return List.of();

        return snapshotRepository.findByPeriodAndSnapshotDate(period, latest).stream()
            .filter(s -> s.getScore() > 0)
            .sorted(Comparator.comparingInt(ScoreSnapshot::getScore).reversed())
            .limit(limit)
            .map(s -> new ScoreBoardEntry(s.getUsername(), s.getTeam(), s.getScore()))
            .toList();
    }

    /**
        * 월간 랭킹: 이번 달 일간 스냅샷 합산.
        * 주간 리셋 후에도 월간 누적이 유지되도록 날짜 범위를 모읍니다.
        */
    private List<ScoreBoardEntry> monthlyAggregateRanking(int limit) {
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
            .limit(limit)
            .toList();
    }
}
