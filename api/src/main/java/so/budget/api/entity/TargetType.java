package so.budget.api.entity;

/**
 * Official YNAB API Target Types:
 * - TB: Target Category Balance
 * - TBD: Target Category Balance by Date
 * - MF: Monthly Funding
 * - NEED: Plan Your Spending
 * - DEBT: Debt / Loan payment
 */
public enum TargetType {
    TB,
    TBD,
    MF,
    NEED,
    DEBT
}
