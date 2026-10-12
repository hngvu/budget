package so.budget.api.entity;

/**
 * Modern YNAB Target types / behaviors:
 * - SET_ASIDE: Set aside a fixed amount every period regardless of leftover balance.
 * - REFILL: Top off to reach the target amount (unspent balance reduces what needs to be assigned).
 * - HAVE_BALANCE: Maintain or reach a target balance by a specific due date (Custom targets).
 */
public enum GoalType {
    SET_ASIDE,
    REFILL,
    HAVE_BALANCE
}
