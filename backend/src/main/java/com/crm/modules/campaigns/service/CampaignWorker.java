package com.crm.modules.campaigns.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Background sender: scans for due campaign recipients every minute (single short batch). */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignWorker {

    private final CampaignService campaignService;

    @Scheduled(fixedDelayString = "60000", initialDelayString = "30000")
    public void tick() {
        try {
            int sent = campaignService.processDueBatch(50);
            if (sent > 0) log.info("Campaign worker sent {} emails", sent);
        } catch (Exception e) {
            log.error("Campaign worker cycle failed", e);
        }
    }
}
