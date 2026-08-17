package com.careflow.followup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes out follow-ups whose scheduled date has passed without a response, so
 * the dashboard reflects reality rather than an ever-growing backlog of tasks
 * that are nominally still "scheduled".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpSweepJob {

    private final FollowUpService followUpService;

    @Scheduled(cron = "${careflow.jobs.overdue-sweep-cron:0 15 1 * * *}")
    public void markOverdueFollowUps() {
        int marked = followUpService.markOverdueAsMissed();
        if (marked > 0) {
            log.info("Overdue sweep marked {} follow-ups as missed", marked);
        }
    }
}
