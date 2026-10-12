-- ============================================================================
-- BudgetFlow - V1__init.sql  (PostgreSQL 13+, Flyway)
-- YNAB-style zero-based budgeting, MVP scope.
--
-- Conventions
--   * Money is BIGINT in milliunits: 1000 = 1 currency unit. Outflow < 0, inflow > 0.
--     For currencies with 0 decimals (VND) the service must reject amounts
--     that are not multiples of 1000.
--   * Every "month" column is the first day of the month (yyyy-MM-01).
--   * Single source of truth: balances, Available and Ready to Assign are
--     computed (functions below), never stored.
--   * Tenant isolation: every table carries budget_id, and composite FKs
--     (budget_id, id) make it impossible to link rows across budgets.
--   * Hard delete. Accounts and categories that have transactions cannot be
--     deleted (use closed / hidden); deleting a budget cascades everything.
--
-- Deliberately NOT in this migration (add later as V2, V3...):
--   credit cards + payment categories, off-budget (tracking) accounts,
--   split transactions, scheduled transactions, reconciliation, loans.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- users / budgets
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100),
    plan          VARCHAR(10)  NOT NULL DEFAULT 'FREE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_plan CHECK (plan IN ('FREE', 'PRO'))
);

CREATE TABLE budgets (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    currency_code  CHAR(3)  NOT NULL DEFAULT 'VND',
    decimal_digits SMALLINT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_budgets_digits CHECK (decimal_digits BETWEEN 0 AND 3)
);
CREATE INDEX idx_budgets_user ON budgets(user_id);

-- ----------------------------------------------------------------------------
-- accounts / payees
-- ----------------------------------------------------------------------------
CREATE TABLE accounts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id  UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    type       VARCHAR(16)  NOT NULL,
    closed     BOOLEAN NOT NULL DEFAULT FALSE,
    note       TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT NOT NULL DEFAULT 0,
    UNIQUE (budget_id, id),
    CONSTRAINT chk_accounts_type CHECK (type IN ('cash', 'checking', 'savings'))
);
CREATE UNIQUE INDEX uq_accounts_name ON accounts(budget_id, lower(name));

CREATE TABLE payees (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id  UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (budget_id, id)
);
CREATE UNIQUE INDEX uq_payees_name ON payees(budget_id, lower(name));

-- ----------------------------------------------------------------------------
-- category groups / categories (with targets)
-- ----------------------------------------------------------------------------
CREATE TABLE category_groups (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id  UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    hidden     BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (budget_id, id)
);
CREATE UNIQUE INDEX uq_category_groups_name ON category_groups(budget_id, lower(name));

CREATE TABLE categories (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id         UUID NOT NULL,
    category_group_id UUID NOT NULL,
    name              VARCHAR(255) NOT NULL,
    hidden            BOOLEAN NOT NULL DEFAULT FALSE,
    note              TEXT,
    sort_order        INT NOT NULL DEFAULT 0,
    -- 'INFLOW_READY_TO_ASSIGN' marks the hidden category that receives income.
    system_kind       VARCHAR(32),

    -- Targets. A target is one row of config per category; progress
    -- (needed this month, underfunded...) is computed by the service.
    goal_type              VARCHAR(16),   -- SET_ASIDE | REFILL | HAVE_BALANCE
    goal_target            BIGINT,        -- milliunits
    goal_cadence           VARCHAR(8),    -- WEEKLY | MONTHLY | YEARLY | CUSTOM
    goal_cadence_frequency SMALLINT NOT NULL DEFAULT 1,
    goal_day               SMALLINT,      -- 1-7 weekly (day of week), 1-31 otherwise
    goal_target_month      DATE,          -- due month for CUSTOM targets
    goal_repeat            BOOLEAN NOT NULL DEFAULT TRUE,
    goal_snoozed_month     DATE,          -- month in which the target is snoozed

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT NOT NULL DEFAULT 0,

    UNIQUE (budget_id, id),
    FOREIGN KEY (budget_id, category_group_id) REFERENCES category_groups(budget_id, id),

    CONSTRAINT chk_cat_system_kind CHECK (system_kind IS NULL OR system_kind = 'INFLOW_READY_TO_ASSIGN'),
    CONSTRAINT chk_goal_type    CHECK (goal_type IN ('SET_ASIDE', 'REFILL', 'HAVE_BALANCE')),
    CONSTRAINT chk_goal_cadence CHECK (goal_cadence IN ('WEEKLY', 'MONTHLY', 'YEARLY', 'CUSTOM')),
    CONSTRAINT chk_goal_target  CHECK (goal_target > 0),
    CONSTRAINT chk_goal_freq    CHECK (goal_cadence_frequency >= 1),
    CONSTRAINT chk_goal_months  CHECK (
        (goal_target_month  IS NULL OR EXTRACT(DAY FROM goal_target_month)  = 1) AND
        (goal_snoozed_month IS NULL OR EXTRACT(DAY FROM goal_snoozed_month) = 1)),
    -- a target is either fully set or not set at all
    CONSTRAINT chk_goal_complete CHECK (
        (goal_type IS NULL) = (goal_target IS NULL) AND
        (goal_type IS NULL) = (goal_cadence IS NULL)),
    -- "Have a balance of..." exists only as a custom target and never repeats
    CONSTRAINT chk_goal_have_balance CHECK (
        goal_type IS DISTINCT FROM 'HAVE_BALANCE'
        OR (goal_cadence = 'CUSTOM' AND goal_repeat = FALSE)),
    -- weekly / monthly / yearly targets always repeat; only CUSTOM can opt out
    CONSTRAINT chk_goal_repeat CHECK (
        goal_cadence IS NULL OR goal_cadence = 'CUSTOM' OR goal_repeat),
    CONSTRAINT chk_goal_day CHECK (
        goal_day IS NULL
        OR (goal_cadence = 'WEEKLY' AND goal_day BETWEEN 1 AND 7)
        OR (goal_cadence IS DISTINCT FROM 'WEEKLY' AND goal_day BETWEEN 1 AND 31))
);
CREATE UNIQUE INDEX uq_categories_name ON categories(category_group_id, lower(name));
-- exactly one "Ready to Assign" category per budget
CREATE UNIQUE INDEX uq_categories_rta ON categories(budget_id)
    WHERE system_kind = 'INFLOW_READY_TO_ASSIGN';

-- ----------------------------------------------------------------------------
-- monthly assignment (the only budget data that is stored)
-- ----------------------------------------------------------------------------
CREATE TABLE month_category_budgets (
    category_id UUID   NOT NULL,
    month       DATE   NOT NULL,
    budget_id   UUID   NOT NULL,
    budgeted    BIGINT NOT NULL DEFAULT 0,       -- "Assigned", milliunits
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (category_id, month),
    FOREIGN KEY (budget_id, category_id) REFERENCES categories(budget_id, id) ON DELETE CASCADE,
    CONSTRAINT chk_mcb_month CHECK (EXTRACT(DAY FROM month) = 1)
);
CREATE INDEX idx_mcb_budget_month ON month_category_budgets(budget_id, month);

-- ----------------------------------------------------------------------------
-- transactions
-- ----------------------------------------------------------------------------
CREATE TABLE transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id               UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    account_id              UUID NOT NULL,
    date                    DATE   NOT NULL,
    amount                  BIGINT NOT NULL,      -- (+) inflow, (-) outflow, milliunits
    payee_id                UUID,
    -- income goes to the budget's INFLOW_READY_TO_ASSIGN category;
    -- NULL = uncategorized (or a transfer)
    category_id             UUID,
    memo                    TEXT,
    cleared                 BOOLEAN NOT NULL DEFAULT FALSE,
    -- transfer = two linked rows, one per account
    transfer_account_id     UUID,
    transfer_transaction_id UUID,
    import_id               VARCHAR(255),         -- bank CSV de-duplication
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0,

    UNIQUE (budget_id, id),
    FOREIGN KEY (budget_id, account_id)          REFERENCES accounts(budget_id, id),
    FOREIGN KEY (budget_id, payee_id)            REFERENCES payees(budget_id, id),
    FOREIGN KEY (budget_id, category_id)         REFERENCES categories(budget_id, id),
    FOREIGN KEY (budget_id, transfer_account_id) REFERENCES accounts(budget_id, id),
    -- the cascade is immediate; DEFERRABLE only lets both rows of a pair be inserted
    FOREIGN KEY (budget_id, transfer_transaction_id) REFERENCES transactions(budget_id, id)
        ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,

    CONSTRAINT chk_tx_amount   CHECK (amount <> 0),
    CONSTRAINT chk_tx_pair     CHECK ((transfer_account_id IS NULL) = (transfer_transaction_id IS NULL)),
    CONSTRAINT chk_tx_not_self CHECK (transfer_account_id IS NULL OR transfer_account_id <> account_id),
    -- all accounts are on-budget, so a transfer never has a category
    CONSTRAINT chk_tx_transfer_no_category CHECK (transfer_account_id IS NULL OR category_id IS NULL)
);
CREATE INDEX idx_tx_account_date  ON transactions(account_id, date DESC);
CREATE INDEX idx_tx_budget_date   ON transactions(budget_id, date);
CREATE INDEX idx_tx_category_date ON transactions(category_id, date);
CREATE UNIQUE INDEX uq_tx_import  ON transactions(account_id, import_id) WHERE import_id IS NOT NULL;

-- ============================================================================
-- Triggers
-- ============================================================================

-- A new budget always gets its hidden "Ready to Assign" category.
CREATE OR REPLACE FUNCTION fn_budget_defaults() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE g UUID;
BEGIN
    INSERT INTO category_groups (budget_id, name, hidden, sort_order)
    VALUES (NEW.id, 'Internal Master Category', TRUE, -1)
    RETURNING id INTO g;

    INSERT INTO categories (budget_id, category_group_id, name, hidden, system_kind)
    VALUES (NEW.id, g, 'Inflow: Ready to Assign', TRUE, 'INFLOW_READY_TO_ASSIGN');
    RETURN NULL;
END $$;
CREATE TRIGGER trg_budget_defaults AFTER INSERT ON budgets
    FOR EACH ROW EXECUTE FUNCTION fn_budget_defaults();

-- The two legs of a transfer must mirror each other. Checked at COMMIT so both
-- rows can be inserted in one transaction.
CREATE OR REPLACE FUNCTION fn_check_transfer() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p transactions%ROWTYPE;
BEGIN
    IF NEW.transfer_transaction_id IS NULL THEN RETURN NULL; END IF;
    SELECT * INTO p FROM transactions WHERE id = NEW.transfer_transaction_id;
    IF NOT FOUND
       OR p.transfer_transaction_id IS DISTINCT FROM NEW.id
       OR p.account_id <> NEW.transfer_account_id
       OR p.transfer_account_id IS DISTINCT FROM NEW.account_id
       OR p.amount <> -NEW.amount
       OR p.date <> NEW.date THEN
        RAISE EXCEPTION 'Transfer % does not match its counterpart %',
            NEW.id, NEW.transfer_transaction_id;
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_transfer AFTER INSERT OR UPDATE ON transactions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION fn_check_transfer();

-- ============================================================================
-- Calculations (functions, so the budget_id filter applies BEFORE the recursion)
--
--   activity(c, m)  = sum of categorized transactions in month m
--   available(c, m) = max(available(c, m-1), 0) + assigned(c, m) + activity(c, m)
--                     (overspending is not carried over; it is taken out of
--                      Ready to Assign of the following month instead)
--   Ready to Assign(m) = income up to m - assigned up to m
--                        + overspending of all months before m
--   Months are generated from the first transaction / assignment up to p_until,
--   so nothing is silently ignored.
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_first_month(p_budget_id UUID, p_until DATE) RETURNS DATE
LANGUAGE sql STABLE AS $$
    SELECT date_trunc('month', LEAST(
        p_until,
        COALESCE((SELECT min(t.date)  FROM transactions t            WHERE t.budget_id = p_budget_id), p_until),
        COALESCE((SELECT min(m.month) FROM month_category_budgets m  WHERE m.budget_id = p_budget_id), p_until)
    ))::date
$$;

CREATE OR REPLACE FUNCTION fn_category_months(
    p_budget_id UUID,
    p_until     DATE DEFAULT (date_trunc('month', CURRENT_DATE)::date)
) RETURNS TABLE (category_id UUID, month DATE, budgeted BIGINT, activity BIGINT, available BIGINT)
LANGUAGE sql STABLE AS $$
    WITH RECURSIVE
    cal AS (
        SELECT c.id AS cat_id, d::date AS mon,
               row_number() OVER (PARTITION BY c.id ORDER BY d) AS n
        FROM categories c
        CROSS JOIN generate_series(fn_first_month(p_budget_id, p_until),
                                   date_trunc('month', p_until)::date,
                                   interval '1 month') d
        WHERE c.budget_id = p_budget_id
          AND c.system_kind IS DISTINCT FROM 'INFLOW_READY_TO_ASSIGN'
    ),
    act AS (
        SELECT t.category_id AS cat_id, date_trunc('month', t.date)::date AS mon,
               sum(t.amount)::bigint AS activity
        FROM transactions t
        WHERE t.budget_id = p_budget_id AND t.category_id IS NOT NULL
        GROUP BY 1, 2
    ),
    base AS (
        SELECT cal.cat_id, cal.mon, cal.n,
               COALESCE(m.budgeted, 0)::bigint  AS budgeted,
               COALESCE(act.activity, 0)::bigint AS activity
        FROM cal
        LEFT JOIN month_category_budgets m ON m.category_id = cal.cat_id AND m.month = cal.mon
        LEFT JOIN act ON act.cat_id = cal.cat_id AND act.mon = cal.mon
    ),
    calc AS (
        SELECT cat_id, mon, n, budgeted, activity, (budgeted + activity)::bigint AS available
        FROM base WHERE n = 1
        UNION ALL
        SELECT b.cat_id, b.mon, b.n, b.budgeted, b.activity,
               (GREATEST(p.available, 0) + b.budgeted + b.activity)::bigint
        FROM calc p
        JOIN base b ON b.cat_id = p.cat_id AND b.n = p.n + 1
    )
    SELECT cat_id, mon, budgeted, activity, available FROM calc
$$;

CREATE OR REPLACE FUNCTION fn_month_summary(
    p_budget_id UUID,
    p_until     DATE DEFAULT (date_trunc('month', CURRENT_DATE)::date)
) RETURNS TABLE (month DATE, income BIGINT, budgeted BIGINT, activity BIGINT,
                 overspent BIGINT, ready_to_assign BIGINT)
LANGUAGE sql STABLE AS $$
    WITH months AS (
        SELECT d::date AS mon
        FROM generate_series(fn_first_month(p_budget_id, p_until),
                             date_trunc('month', p_until)::date,
                             interval '1 month') d
    ),
    cm AS (
        SELECT x.month AS mon,
               sum(x.budgeted)::bigint AS budgeted,
               sum(x.activity)::bigint AS activity,
               sum(LEAST(x.available, 0))::bigint AS overspent
        FROM fn_category_months(p_budget_id, p_until) x
        GROUP BY x.month
    ),
    inflow AS (
        SELECT date_trunc('month', t.date)::date AS mon, sum(t.amount)::bigint AS income
        FROM transactions t
        JOIN categories c ON c.id = t.category_id
                         AND c.system_kind = 'INFLOW_READY_TO_ASSIGN'
        WHERE t.budget_id = p_budget_id
        GROUP BY 1
    ),
    m AS (
        SELECT months.mon,
               COALESCE(inflow.income, 0)::bigint AS income,
               COALESCE(cm.budgeted, 0)::bigint   AS budgeted,
               COALESCE(cm.activity, 0)::bigint   AS activity,
               COALESCE(cm.overspent, 0)::bigint  AS overspent
        FROM months
        LEFT JOIN cm     ON cm.mon = months.mon
        LEFT JOIN inflow ON inflow.mon = months.mon
    )
    SELECT m.mon, m.income, m.budgeted, m.activity, m.overspent,
           (  sum(m.income)   OVER (ORDER BY m.mon)
            - sum(m.budgeted) OVER (ORDER BY m.mon)
            + COALESCE(sum(m.overspent) OVER (ORDER BY m.mon
                  ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0)
           )::bigint
    FROM m
    ORDER BY m.mon
$$;

-- Account balance = sum of its transactions (nothing is cached).
CREATE OR REPLACE VIEW v_account_balances AS
SELECT a.id AS account_id, a.budget_id,
       COALESCE(sum(t.amount), 0)::bigint AS balance,
       COALESCE(sum(t.amount) FILTER (WHERE t.cleared), 0)::bigint AS cleared_balance
FROM accounts a
LEFT JOIN transactions t ON t.account_id = a.id
GROUP BY a.id, a.budget_id;
