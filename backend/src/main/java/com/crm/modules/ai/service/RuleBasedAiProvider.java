package com.crm.modules.ai.service;

import org.springframework.stereotype.Component;

/**
 * Deterministic offline "AI": template-based drafts and heuristic summaries derived from CRM data.
 * Guarantees the AI feature set works without any external API key (and never leaks data anywhere).
 */
@Component
public class RuleBasedAiProvider implements AiProvider {

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public Result complete(Request request) {
        String body = switch (request.useCase()) {
            case "EMAIL_OUTREACH" -> """
                Hi {{firstName}},

                I came across {{companyName}} while researching leading practices in the area.

                We help teams like yours streamline their outreach and follow-up so no opportunity slips through the cracks. Given your position in {{city}}, I think there may be a quick win here.

                Would a short call next week be reasonable?

                Best regards""";
            case "EMAIL_FOLLOW_UP" -> """
                Hi {{firstName}},

                Just floating my earlier note to the top of your inbox.

                If improving follow-up consistency at {{companyName}} is on your list this quarter, I'd love to show you what that looks like in 15 minutes.

                Best regards""";
            case "EMAIL_MEETING_CONFIRM" -> """
                Hi {{firstName}},

                Looking forward to our call. I'll share a short agenda beforehand and tailor the walkthrough to {{companyName}}.

                If anything changes on your side, just let me know.

                Best regards""";
            case "EMAIL_RE_ENGAGEMENT" -> """
                Hi {{firstName}},

                It's been a while since we last spoke about {{companyName}}. If the timing wasn't right then, it may be worth another look now — a lot has changed on our side.

                Open to a quick catch-up?

                Best regards""";
            default -> request.useCase() + ": " + request.userPrompt();
        };
        return new Result("rule-based", body.strip(), true);
    }
}
