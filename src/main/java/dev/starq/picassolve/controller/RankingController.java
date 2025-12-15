package dev.starq.picassolve.controller;

import dev.starq.picassolve.dto.ScoreBoardEntry;
import dev.starq.picassolve.service.RankingQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingQueryService rankingQueryService;

    @GetMapping
    public List<ScoreBoardEntry> getRanking(@RequestParam(defaultValue = "LIVE") String period) {
        return rankingQueryService.getRanking(period);
    }
}
