package so.budget.api.entity;

/**
 * Recurrence frequency for YNAB Scheduled Transactions
 */
public enum RecurrenceFrequency {
    NEVER,             // One-time future transaction
    DAILY,
    WEEKLY,
    EVERY_OTHER_WEEK,
    TWICE_A_MONTH,
    EVERY_4_WEEKS,
    MONTHLY,
    EVERY_OTHER_MONTH,
    EVERY_3_MONTHS,
    EVERY_4_MONTHS,
    EVERY_6_MONTHS,
    YEARLY,
    EVERY_OTHER_YEAR
}
