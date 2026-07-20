package br.ufes.soe.api.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import br.ufes.soe.api.dto.DashboardSnapshot;
import br.ufes.soe.api.dto.HotStreakEntryDto;
import br.ufes.soe.api.dto.LeaderEntryDto;
import br.ufes.soe.api.dto.RankingEntryDto;
import br.ufes.soe.api.dto.SeasonInfoDto;
import br.ufes.soe.api.service.LiveDashboardService;

@RestController
@RequestMapping("/api")
public class DashboardRestController {

    private final LiveDashboardService dashboard;

    public DashboardRestController(LiveDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @GetMapping("/season")
    public SeasonInfoDto season() {
        return dashboard.seasonInfo();
    }

    @GetMapping("/ranking")
    public List<RankingEntryDto> ranking() {
        return dashboard.ranking();
    }

    @GetMapping("/leaders")
    public List<LeaderEntryDto> leaders() {
        return dashboard.leaders("points");
    }

    @GetMapping("/hot-streak")
    public List<HotStreakEntryDto> hotStreak() {
        return dashboard.hotStreaks();
    }

    @GetMapping("/dashboard")
    public DashboardSnapshot dashboard() {
        return dashboard.snapshot();
    }

    @GetMapping(path = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter liveStream() {
        return dashboard.subscribe();
    }
}
