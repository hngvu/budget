-- ============================================================================
-- BudgetFlow - V1__init.sql  (PostgreSQL 13+, Flyway)
-- Modern YNAB-style zero-based budgeting (Plans, Targets, Assignments), MVP scope.
--
-- Conventions
--   * Money is BIGINT in milliunits: 1000 = 1 currency unit. Outflow < 0, inflow > 0.
--   * Every "month" column is the first day of the month (yyyy-MM-01).
--   * Single source of truth: balances, Available and Ready to Assign are
--     computed (functions below), never stored.
--   * Tenant isolation: every table carries plan_id, and composite FKs
--     (plan_id, id) make it impossible to link rows across plans.
--   * Hard delete. Accounts and categories that have transactions cannot be
--     deleted (use closed / hidden); deleting a plan cascades everything.
--     The composite FKs are DEFERRABLE INITIALLY DEFERRED, so these rules are
--     enforced at COMMIT (use SET CONSTRAINTS ALL IMMEDIATE to fail earlier).
--
-- Deliberately NOT in this migration (add later as V2, V3...):
--   credit cards + payment categories, off-budget (tracking) accounts,
--   split transactions, scheduled transactions, reconciliation, loans.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- users / plans
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    display_name      VARCHAR(100),
    subscription_tier VARCHAR(10)  NOT NULL DEFAULT 'FREE',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_users_subscription_tier CHECK (subscription_tier IN ('FREE', 'PRO'))
);

CREATE TABLE plans (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    currency_code  CHAR(3)  NOT NULL,
    decimal_digits SMALLINT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_plans_digits CHECK (decimal_digits BETWEEN 0 AND 3)
);
CREATE INDEX idx_plans_user ON plans(user_id);

-- ----------------------------------------------------------------------------
-- accounts / payees
-- ----------------------------------------------------------------------------
CREATE TABLE accounts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id    UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    type       VARCHAR(16)  NOT NULL,
    closed     BOOLEAN NOT NULL DEFAULT FALSE,
    note       TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT NOT NULL DEFAULT 0,
    UNIQUE (plan_id, id),
    CONSTRAINT chk_accounts_type CHECK (type IN ('CASH', 'CHECKING', 'SAVINGS'))
);
CREATE UNIQUE INDEX uq_accounts_name ON accounts(plan_id, lower(name));

CREATE TABLE payees (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id    UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT NOT NULL DEFAULT 0,
    UNIQUE (plan_id, id)
);
CREATE UNIQUE INDEX uq_payees_name ON payees(plan_id, lower(name));

-- ----------------------------------------------------------------------------
-- category groups / categories (with targets)
-- ----------------------------------------------------------------------------
CREATE TABLE category_groups (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id    UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    hidden     BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT NOT NULL DEFAULT 0,
    UNIQUE (plan_id, id)
);
CREATE UNIQUE INDEX uq_category_groups_name ON category_groups(plan_id, lower(name));

CREATE TABLE categories (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id           UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    category_group_id UUID NOT NULL,
    name              VARCHAR(255) NOT NULL,
    hidden            BOOLEAN NOT NULL DEFAULT FALSE,
    note              TEXT,
    sort_order        INT NOT NULL DEFAULT 0,
    -- 'INFLOW_READY_TO_ASSIGN' marks the hidden category that receives income.
    system_kind       VARCHAR(32),

    -- Targets (Official YNAB API Schema)
    target_type               VARCHAR(16),  -- TB | TBD | MF | NEED | DEBT
    target_amount             BIGINT,       -- milliunits
    target_date               DATE,         -- due date for targets by date (yyyy-MM-dd)
    target_creation_month     DATE,         -- creation month of the target (yyyy-MM-01)
    target_cadence            INT,          -- 0=no repeat, 1=monthly, 2=weekly, 13=yearly, etc.
    target_cadence_frequency  INT,          -- multiplier used with cadence 1 or 2
    target_day                INT,          -- weekly: 0=Sun..6=Sat; monthly/yearly: 1..31
    target_needs_whole_amount BOOLEAN,      -- true: Set aside another, false: Refill up to (applies to NEED)
    target_snoozed_month      DATE,         -- extension: month in which target is snoozed (yyyy-MM-01)

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT NOT NULL DEFAULT 0,

    UNIQUE (plan_id, id),
    FOREIGN KEY (plan_id, category_group_id) REFERENCES category_groups(plan_id, id)
        DEFERRABLE INITIALLY DEFERRED,

    CONSTRAINT chk_cat_system_kind CHECK (system_kind IS NULL OR system_kind = 'INFLOW_READY_TO_ASSIGN'),
    CONSTRAINT chk_target_type     CHECK (target_type IN ('TB', 'TBD', 'MF', 'NEED', 'DEBT')),
    CONSTRAINT chk_target_amount   CHECK (target_amount > 0),
    CONSTRAINT chk_target_cadence  CHECK (target_cadence >= 0),
    CONSTRAINT chk_target_cadence_freq CHECK (target_cadence_frequency IS NULL OR target_cadence_frequency >= 1),
    CONSTRAINT chk_target_months   CHECK (
        (target_creation_month IS NULL OR EXTRACT(DAY FROM target_creation_month) = 1) AND
        (target_snoozed_month  IS NULL OR EXTRACT(DAY FROM target_snoozed_month)  = 1)),
    CONSTRAINT chk_target_day CHECK (
        target_day IS NULL
        OR (target_cadence = 2 AND target_day BETWEEN 0 AND 6)
        OR (target_cadence IS DISTINCT FROM 2 AND target_day BETWEEN 1 AND 31))
);
CREATE UNIQUE INDEX uq_categories_name ON categories(category_group_id, lower(name));
-- exactly one "Ready to Assign" category per plan
CREATE UNIQUE INDEX uq_categories_rta ON categories(plan_id)
    WHERE system_kind = 'INFLOW_READY_TO_ASSIGN';

-- ----------------------------------------------------------------------------
-- monthly assignment (the only budget/plan data that is stored)
-- ----------------------------------------------------------------------------
CREATE TABLE month_category_assignments (
    category_id UUID   NOT NULL,
    month       DATE   NOT NULL,
    plan_id     UUID   NOT NULL,
    assigned    BIGINT NOT NULL DEFAULT 0,       -- "Assigned", milliunits
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (category_id, month),
    FOREIGN KEY (plan_id, category_id) REFERENCES categories(plan_id, id) ON DELETE CASCADE,
    CONSTRAINT chk_mca_month CHECK (EXTRACT(DAY FROM month) = 1)
);
CREATE INDEX idx_mca_plan_month ON month_category_assignments(plan_id, month);

-- ----------------------------------------------------------------------------
-- transactions
-- ----------------------------------------------------------------------------
CREATE TABLE transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id                 UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    account_id              UUID NOT NULL,
    date                    DATE   NOT NULL,
    amount                  BIGINT NOT NULL,      -- (+) inflow, (-) outflow, milliunits
    payee_id                UUID,
    -- income goes to the plan's INFLOW_READY_TO_ASSIGN category;
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

    UNIQUE (plan_id, id),
    FOREIGN KEY (plan_id, account_id)          REFERENCES accounts(plan_id, id)        DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (plan_id, payee_id)            REFERENCES payees(plan_id, id)          DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (plan_id, category_id)         REFERENCES categories(plan_id, id)      DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (plan_id, transfer_account_id) REFERENCES accounts(plan_id, id)        DEFERRABLE INITIALLY DEFERRED,
    -- the cascade is immediate; DEFERRABLE only lets both rows of a pair be inserted
    FOREIGN KEY (plan_id, transfer_transaction_id) REFERENCES transactions(plan_id, id)
        ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,

    CONSTRAINT chk_tx_amount   CHECK (amount <> 0),
    CONSTRAINT chk_tx_pair     CHECK ((transfer_account_id IS NULL) = (transfer_transaction_id IS NULL)),
    CONSTRAINT chk_tx_not_self CHECK (transfer_account_id IS NULL OR transfer_account_id <> account_id),
    -- all accounts are on-budget, so a transfer never has a category
    CONSTRAINT chk_tx_transfer_no_category CHECK (transfer_account_id IS NULL OR category_id IS NULL)
);
CREATE INDEX idx_tx_account_date  ON transactions(account_id, date DESC);
CREATE INDEX idx_tx_plan_date     ON transactions(plan_id, date);
CREATE INDEX idx_tx_category_date ON transactions(category_id, date);
CREATE UNIQUE INDEX uq_tx_import  ON transactions(account_id, import_id) WHERE import_id IS NOT NULL;

-- ============================================================================
-- Triggers
-- ============================================================================

-- A new plan always gets its hidden "Ready to Assign" category.
CREATE FUNCTION fn_plan_defaults() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE g UUID;
BEGIN
    INSERT INTO category_groups (plan_id, name, hidden, sort_order)
    VALUES (NEW.id, 'Internal Master Category', TRUE, -1)
    RETURNING id INTO g;

    INSERT INTO categories (plan_id, category_group_id, name, hidden, system_kind)
    VALUES (NEW.id, g, 'Inflow: Ready to Assign', TRUE, 'INFLOW_READY_TO_ASSIGN');
    RETURN NULL;
END $$;
CREATE TRIGGER trg_plan_defaults AFTER INSERT ON plans
    FOR EACH ROW EXECUTE FUNCTION fn_plan_defaults();

-- The two legs of a transfer must mirror each other. Checked at COMMIT so both
-- rows can be inserted in one transaction.
CREATE FUNCTION fn_check_transfer() RETURNS trigger LANGUAGE plpgsql AS $$
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
-- Calculations (functions, so the plan_id filter applies BEFORE the recursion)
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

CREATE FUNCTION fn_first_month(p_plan_id UUID, p_until DATE) RETURNS DATE
LANGUAGE sql STABLE AS $$
    SELECT date_trunc('month', LEAST(
        p_until,
        COALESCE((SELECT min(t.date)  FROM transactions t                 WHERE t.plan_id = p_plan_id), p_until),
        COALESCE((SELECT min(m.month) FROM month_category_assignments m   WHERE m.plan_id = p_plan_id), p_until)
    ))::date
$$;

CREATE FUNCTION fn_category_months(
    p_plan_id UUID,
    p_until   DATE DEFAULT (date_trunc('month', CURRENT_DATE)::date)
) RETURNS TABLE (category_id UUID, month DATE, assigned BIGINT, activity BIGINT, available BIGINT)
LANGUAGE sql STABLE AS $$
    WITH RECURSIVE
    cal AS (
        SELECT c.id AS cat_id, d::date AS mon,
               row_number() OVER (PARTITION BY c.id ORDER BY d) AS n
        FROM categories c
        CROSS JOIN generate_series(fn_first_month(p_plan_id, p_until),
                                   date_trunc('month', p_until)::date,
                                   interval '1 month') d
        WHERE c.plan_id = p_plan_id
          AND c.system_kind IS DISTINCT FROM 'INFLOW_READY_TO_ASSIGN'
    ),
    act AS (
        SELECT t.category_id AS cat_id, date_trunc('month', t.date)::date AS mon,
               sum(t.amount)::bigint AS activity
        FROM transactions t
        WHERE t.plan_id = p_plan_id AND t.category_id IS NOT NULL
        GROUP BY 1, 2
    ),
    base AS (
        SELECT cal.cat_id, cal.mon, cal.n,
               COALESCE(m.assigned, 0)::bigint  AS assigned,
               COALESCE(act.activity, 0)::bigint AS activity
        FROM cal
        LEFT JOIN month_category_assignments m ON m.category_id = cal.cat_id AND m.month = cal.mon
        LEFT JOIN act ON act.cat_id = cal.cat_id AND act.mon = cal.mon
    ),
    calc AS (
        SELECT cat_id, mon, n, assigned, activity, (assigned + activity)::bigint AS available
        FROM base WHERE n = 1
        UNION ALL
        SELECT b.cat_id, b.mon, b.n, b.assigned, b.activity,
               (GREATEST(p.available, 0) + b.assigned + b.activity)::bigint
        FROM calc p
        JOIN base b ON b.cat_id = p.cat_id AND b.n = p.n + 1
    )
    SELECT cat_id, mon, assigned, activity, available FROM calc
$$;

CREATE FUNCTION fn_month_summary(
    p_plan_id UUID,
    p_until   DATE DEFAULT (date_trunc('month', CURRENT_DATE)::date)
) RETURNS TABLE (month DATE, income BIGINT, assigned BIGINT, activity BIGINT,
                 overspent BIGINT, ready_to_assign BIGINT)
LANGUAGE sql STABLE AS $$
    WITH months AS (
        SELECT d::date AS mon
        FROM generate_series(fn_first_month(p_plan_id, p_until),
                             date_trunc('month', p_until)::date,
                             interval '1 month') d
    ),
    cm AS (
        SELECT x.month AS mon,
               sum(x.assigned)::bigint AS assigned,
               sum(x.activity)::bigint AS activity,
               sum(LEAST(x.available, 0))::bigint AS overspent
        FROM fn_category_months(p_plan_id, p_until) x
        GROUP BY x.month
    ),
    inflow AS (
        SELECT date_trunc('month', t.date)::date AS mon, sum(t.amount)::bigint AS income
        FROM transactions t
        JOIN categories c ON c.id = t.category_id
                         AND c.system_kind = 'INFLOW_READY_TO_ASSIGN'
        WHERE t.plan_id = p_plan_id
        GROUP BY 1
    ),
    m AS (
        SELECT months.mon,
               COALESCE(inflow.income, 0)::bigint AS income,
               COALESCE(cm.assigned, 0)::bigint   AS assigned,
               COALESCE(cm.activity, 0)::bigint   AS activity,
               COALESCE(cm.overspent, 0)::bigint  AS overspent
        FROM months
        LEFT JOIN cm     ON cm.mon = months.mon
        LEFT JOIN inflow ON inflow.mon = months.mon
    )
    SELECT m.mon, m.income, m.assigned, m.activity, m.overspent,
           (  sum(m.income)   OVER (ORDER BY m.mon)
            - sum(m.assigned) OVER (ORDER BY m.mon)
            + COALESCE(sum(m.overspent) OVER (ORDER BY m.mon
                  ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0)
           )::bigint
    FROM m
    ORDER BY m.mon
$$;

-- Account balance = sum of its transactions (nothing is cached).
CREATE VIEW v_account_balances AS
SELECT a.id AS account_id, a.plan_id,
       COALESCE(sum(t.amount), 0)::bigint AS balance,
       COALESCE(sum(t.amount) FILTER (WHERE t.cleared), 0)::bigint AS cleared_balance
FROM accounts a
LEFT JOIN transactions t ON t.account_id = a.id
GROUP BY a.id, a.plan_id;
