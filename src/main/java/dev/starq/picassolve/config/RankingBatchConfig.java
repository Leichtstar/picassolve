package dev.starq.picassolve.config;

import dev.starq.picassolve.entity.ScoreSnapshot;
import dev.starq.picassolve.entity.ScoreSnapshot.SnapshotPeriod;
import dev.starq.picassolve.repository.ScoreSnapshotRepository;
import dev.starq.picassolve.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class RankingBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final UserRepository userRepository;
    private final ScoreSnapshotRepository snapshotRepository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Job dailyScoreSnapshotJob() {
        return new JobBuilder("dailyScoreSnapshotJob", jobRepository)
            .start(dailyScoreSnapshotStep())
            .build();
    }

    @Bean
    public Job weeklyResetScoresJob() {
        return new JobBuilder("weeklyResetScoresJob", jobRepository)
            .start(weeklyScoreSnapshotStep())
            .next(resetScoresStep())
            .build();
    }

    @Bean
    public Step dailyScoreSnapshotStep() {
        return new StepBuilder("dailyScoreSnapshotStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                LocalDate today = LocalDate.now(KST);
                snapshotRepository.deleteBySnapshotDateAndPeriod(today, SnapshotPeriod.DAILY);

                List<ScoreSnapshot> snapshots = userRepository.findAll().stream()
                    .map(u -> ScoreSnapshot.builder()
                        .userId(u.getId())
                        .username(u.getName())
                        .team(u.getTeam())
                        .score(u.getScore())
                        .snapshotDate(today)
                        .period(SnapshotPeriod.DAILY)
                        .build())
                    .toList();

                snapshotRepository.saveAll(snapshots);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    @Bean
    public Step weeklyScoreSnapshotStep() {
        return new StepBuilder("weeklyScoreSnapshotStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                LocalDate today = LocalDate.now(KST);
                snapshotRepository.deleteBySnapshotDateAndPeriod(today, SnapshotPeriod.WEEKLY);

                List<ScoreSnapshot> snapshots = userRepository.findAll().stream()
                    .map(u -> ScoreSnapshot.builder()
                        .userId(u.getId())
                        .username(u.getName())
                        .team(u.getTeam())
                        .score(u.getScore())
                        .snapshotDate(today)
                        .period(SnapshotPeriod.WEEKLY)
                        .build())
                    .toList();

                snapshotRepository.saveAll(snapshots);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    @Bean
    public Step resetScoresStep() {
        return new StepBuilder("resetScoresStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                userRepository.resetAllScores();
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }
}
