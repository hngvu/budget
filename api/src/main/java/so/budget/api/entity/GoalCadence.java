package so.budget.api.entity;

/**
 * Cadence (frequency period) for YNAB Targets
 */
public enum GoalCadence {
    /**
     * Repeats weekly (e.g. Groceries every week on Friday)
     */
    WEEKLY,

    /**
     * Repeats monthly (e.g. Rent on the 1st, Utilities on the 15th)
     */
    MONTHLY,

    /**
     * Repeats yearly (e.g. Car Insurance due every August)
     */
    YEARLY,

    /**
     * Custom recurrence based on goalCadenceFrequency (e.g. every 3 months / quarterly, every 2 years)
     */
    CUSTOM
}
