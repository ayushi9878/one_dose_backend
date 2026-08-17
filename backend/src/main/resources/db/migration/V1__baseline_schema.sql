-- CareFlow baseline schema.
-- Enum-backed columns are stored as VARCHAR to match Hibernate's EnumType.STRING
-- mapping, which keeps values readable in the database and avoids fragile
-- ordinal coupling when new constants are added.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(190) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BIT(1)       NOT NULL DEFAULT b'1',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY idx_users_email (email),
    KEY idx_users_role (role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE patients (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    medical_record_number VARCHAR(32)  NOT NULL,
    first_name            VARCHAR(80)  NOT NULL,
    last_name             VARCHAR(80)  NOT NULL,
    date_of_birth         DATE         NOT NULL,
    phone                 VARCHAR(20)  NULL,
    email                 VARCHAR(190) NULL,
    primary_condition     VARCHAR(200) NULL,
    discharge_date        DATE         NULL,
    current_risk_level    VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    active                BIT(1)       NOT NULL DEFAULT b'1',
    user_id               BIGINT       NULL,
    care_manager_id       BIGINT       NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY idx_patients_mrn (medical_record_number),
    UNIQUE KEY uk_patients_user (user_id),
    KEY idx_patients_name (last_name, first_name),
    KEY idx_patients_risk (current_risk_level),
    KEY idx_patients_care_manager (care_manager_id),
    CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_patients_care_manager FOREIGN KEY (care_manager_id) REFERENCES users (id)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE medications (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id    BIGINT       NOT NULL,
    medicine_name VARCHAR(160) NOT NULL,
    dosage        VARCHAR(80)  NOT NULL,
    frequency     VARCHAR(32)  NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NULL,
    instructions  VARCHAR(500) NULL,
    active        BIT(1)       NOT NULL DEFAULT b'1',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_medications_patient (patient_id),
    KEY idx_medications_active (patient_id, active),
    CONSTRAINT fk_medications_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE care_plans (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    patient_id BIGINT        NOT NULL,
    start_date DATE          NOT NULL,
    end_date   DATE          NULL,
    plan_type  VARCHAR(32)   NOT NULL,
    status     VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    notes      VARCHAR(1000) NULL,
    created_at DATETIME(6)   NOT NULL,
    updated_at DATETIME(6)   NOT NULL,
    version    BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_care_plans_patient (patient_id),
    KEY idx_care_plans_status (status),
    CONSTRAINT fk_care_plans_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE follow_up_tasks (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    patient_id     BIGINT      NOT NULL,
    care_plan_id   BIGINT      NULL,
    scheduled_date DATE        NOT NULL,
    completed_date DATETIME(6) NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    type           VARCHAR(32) NOT NULL,
    day_offset     INT         NULL,
    title          VARCHAR(200) NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_follow_ups_patient (patient_id),
    KEY idx_follow_ups_care_plan (care_plan_id),
    KEY idx_follow_ups_scheduled (scheduled_date),
    KEY idx_follow_ups_status (status),
    CONSTRAINT fk_follow_ups_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_follow_ups_care_plan FOREIGN KEY (care_plan_id) REFERENCES care_plans (id)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE patient_responses (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    patient_id         BIGINT        NOT NULL,
    follow_up_task_id  BIGINT        NOT NULL,
    medication_taken   BIT(1)        NOT NULL,
    missed_doses       INT           NOT NULL DEFAULT 0,
    symptoms_reported  BIT(1)        NOT NULL,
    refill_needed      BIT(1)        NOT NULL,
    notes              VARCHAR(1000) NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY idx_responses_follow_up (follow_up_task_id),
    KEY idx_responses_patient (patient_id),
    CONSTRAINT fk_responses_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_responses_follow_up FOREIGN KEY (follow_up_task_id) REFERENCES follow_up_tasks (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE patient_response_symptoms (
    patient_response_id BIGINT       NOT NULL,
    symptom             VARCHAR(120) NULL,
    KEY idx_response_symptoms (patient_response_id),
    CONSTRAINT fk_response_symptoms FOREIGN KEY (patient_response_id) REFERENCES patient_responses (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE adherence_events (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    patient_id        BIGINT      NOT NULL,
    follow_up_task_id BIGINT      NULL,
    recorded_date     DATE        NOT NULL,
    expected_doses    INT         NOT NULL,
    taken_doses       INT         NOT NULL,
    missed_doses      INT         NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_adherence_patient (patient_id),
    KEY idx_adherence_recorded (patient_id, recorded_date),
    CONSTRAINT fk_adherence_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_adherence_follow_up FOREIGN KEY (follow_up_task_id) REFERENCES follow_up_tasks (id)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE risk_signals (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id            BIGINT       NOT NULL,
    patient_response_id   BIGINT       NULL,
    code                  VARCHAR(40)  NOT NULL,
    risk_level            VARCHAR(20)  NOT NULL,
    detail                VARCHAR(300) NOT NULL,
    requires_human_review BIT(1)       NOT NULL DEFAULT b'0',
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_risk_signals_patient (patient_id),
    KEY idx_risk_signals_level (risk_level),
    KEY idx_risk_signals_response (patient_response_id),
    CONSTRAINT fk_risk_signals_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_risk_signals_response FOREIGN KEY (patient_response_id) REFERENCES patient_responses (id)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE escalations (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    patient_id               BIGINT        NOT NULL,
    patient_response_id      BIGINT        NULL,
    severity                 VARCHAR(20)   NOT NULL,
    reason                   VARCHAR(500)  NOT NULL,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    assigned_care_manager_id BIGINT        NULL,
    assigned_at              DATETIME(6)   NULL,
    resolved_at              DATETIME(6)   NULL,
    resolved_by_id           BIGINT        NULL,
    resolution_notes         VARCHAR(2000) NULL,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_escalations_patient (patient_id),
    KEY idx_escalations_status (status),
    KEY idx_escalations_severity (severity),
    KEY idx_escalations_assignee (assigned_care_manager_id),
    CONSTRAINT fk_escalations_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_escalations_response FOREIGN KEY (patient_response_id) REFERENCES patient_responses (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_escalations_assignee FOREIGN KEY (assigned_care_manager_id) REFERENCES users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_escalations_resolver FOREIGN KEY (resolved_by_id) REFERENCES users (id)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Audit rows are append-only and must survive deletion of the records they
-- describe, so patient and actor are stored as plain ids without foreign keys.
CREATE TABLE audit_events (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id  BIGINT       NULL,
    actor_id    BIGINT       NULL,
    actor_name  VARCHAR(120) NULL,
    action      VARCHAR(40)  NOT NULL,
    description VARCHAR(500) NOT NULL,
    metadata    TEXT         NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_audit_patient (patient_id, created_at),
    KEY idx_audit_action (action),
    KEY idx_audit_actor (actor_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
