package br.ufes.soe.api.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import br.ufes.soe.api.service.LiveDashboardService;

@Component
public class KafkaTopicListeners {

    private final LiveDashboardService dashboard;

    public KafkaTopicListeners(LiveDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @KafkaListener(topics = "nba_game", groupId = "dashboard-api-grupo")
    public void onNbaGame(ConsumerRecord<String, String> record) {
        dashboard.onNbaGameMessage(record.key(), record.value());
    }

    @KafkaListener(topics = "odds_game", groupId = "dashboard-api-grupo")
    public void onOdds(ConsumerRecord<String, String> record) {
        dashboard.onOddsMessage(record.key(), record.value());
    }

    @KafkaListener(
            topics = "stats_jogador",
            groupId = "dashboard-api-grupo",
            containerFactory = "integerKafkaListenerContainerFactory")
    public void onPlayerStats(ConsumerRecord<String, Integer> record) {
        dashboard.onPlayerStats(record.key(), record.value());
    }

    @KafkaListener(topics = "stats_time", groupId = "dashboard-api-grupo")
    public void onTeamStats(ConsumerRecord<String, String> record) {
        dashboard.onTeamStats(record.key(), record.value());
    }

    @KafkaListener(topics = "hotstreak_player", groupId = "dashboard-api-grupo")
    public void onHotStreak(ConsumerRecord<String, String> record) {
        dashboard.onHotStreak(record.key(), record.value());
    }

    @KafkaListener(
            topics = "simultaneous_streaks",
            groupId = "dashboard-api-grupo",
            containerFactory = "longKafkaListenerContainerFactory")
    public void onSimultaneousStreak(ConsumerRecord<String, Long> record) {
        dashboard.onSimultaneousStreak(record.value());
    }
}
