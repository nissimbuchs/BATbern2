package ch.batbern.events.dto;

/**
 * Request DTO for manually triggering a speaker reminder.
 * Story 6.5: Automated Deadline Reminders (AC8)
 */
public class SendReminderRequest {

    private String reminderType; // RESPONSE or CONTENT
    private String tier;         // Optional: TIER_1, TIER_2, TIER_3 (auto-detected if omitted)

    public SendReminderRequest() {
    }

    public SendReminderRequest(String reminderType, String tier) {
        this.reminderType = reminderType;
        this.tier = tier;
    }

    public String getReminderType() {
        return reminderType;
    }

    public void setReminderType(String reminderType) {
        this.reminderType = reminderType;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }
}
