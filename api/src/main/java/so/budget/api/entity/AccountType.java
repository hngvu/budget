package so.budget.api.entity;

/**
 * Standard YNAB Account Types.
 * Categorized into:
 * 1. Budget Accounts (on-budget: cash, checking, savings, credit cards)
 * 2. Tracking / Loan Accounts (off-budget: assets, debts, mortgages, loans)
 */
public enum AccountType {
    // --- Budget Accounts (Cash & Spending) ---
    CHECKING,
    SAVINGS,
    CASH,
    CREDIT_CARD,
    LINE_OF_CREDIT,

    // --- Tracking Accounts (Assets & Investments) ---
    OTHER_ASSET,

    // --- Tracking / Liability / Loan Accounts (Debts) ---
    OTHER_LIABILITY,
    MORTGAGE,
    AUTO_LOAN,
    STUDENT_LOAN,
    PERSONAL_LOAN,
    MEDICAL_DEBT,
    OTHER_DEBT
}
