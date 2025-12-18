package dev.starq.picassolve.config;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class RankingJobScheduler {

    private final JobLauncher jobLauncher;
    private final Job dailyScoreSnapshotJob;
    private final Job weeklyResetScoresJob;

    /** 매일 00:00 KST 스코어 스냅샷. */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void runDailySnapshotJob() {
        log.info("[스케줄러] 일간 스코어 스냅샷 작업을 실행합니다.");
        runJob(dailyScoreSnapshotJob);
    }

    /** 매주 월요일 00:00 KST 스코어 스냅샷 후 초기화. */
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    public void runWeeklyResetJob() {
        log.info("[스케줄러] 주간 스코어 초기화 작업을 실행합니다.");
        runJob(weeklyResetScoresJob);
    }

    private void runJob(Job job) {
        JobParameters params = new JobParametersBuilder()
                .addLocalDateTime("runAt", LocalDateTime.now())
                .toJobParameters();
        try {
            jobLauncher.run(job, params);
            log.info("[스케줄러] 배치 작업 '{}' 요청이 성공했습니다.", job.getName());
        } catch (Exception e) {
            log.error("[스케줄러] 배치 작업 '{}' 요청 중 오류 발생: {}", job.getName(), e.getMessage(), e);
        }
    }
}
