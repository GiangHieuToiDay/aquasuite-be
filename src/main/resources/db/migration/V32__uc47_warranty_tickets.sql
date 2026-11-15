-- V32: Triển khai UC-47 Submit Online Warranty Claim (Phiếu yêu cầu bảo hành điện tử trực tuyến)

CREATE TABLE warranty_tickets (
  ticket_id            UUID PRIMARY KEY,
  ticket_code          VARCHAR(50) NOT NULL UNIQUE,
  warranty_record_id   UUID NOT NULL REFERENCES warranty_records(warranty_record_id),
  customer_id          UUID NOT NULL REFERENCES customers(customer_id),
  issue_description    TEXT NOT NULL,
  preferred_date       DATE,
  preferred_slot       VARCHAR(16),
  site_address         TEXT NOT NULL,
  contact_name         VARCHAR(255) NOT NULL,
  contact_phone        VARCHAR(32)  NOT NULL,
  contact_email        VARCHAR(320),
  media                JSONB NOT NULL DEFAULT '[]'::jsonb,
  status               VARCHAR(32) NOT NULL DEFAULT 'NEW',
  technical_job_id     UUID,
  result               TEXT,
  rejection_reason     TEXT,
  cancel_reason        TEXT,
  completed_at         TIMESTAMPTZ,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_ticket_status CHECK (status IN ('NEW','IN_PROGRESS','DONE','REJECTED','CANCELLED')),
  CONSTRAINT ck_ticket_slot   CHECK (preferred_slot IS NULL OR preferred_slot IN ('MORNING','AFTERNOON','EVENING')),
  CONSTRAINT ck_ticket_media  CHECK (jsonb_typeof(media) = 'array')
);

-- Mỗi thẻ bảo hành chỉ có tối đa 1 ticket đang mở (NEW hoặc IN_PROGRESS)
CREATE UNIQUE INDEX uq_warranty_ticket_open
  ON warranty_tickets (warranty_record_id) WHERE status IN ('NEW','IN_PROGRESS');

CREATE INDEX ix_ticket_customer ON warranty_tickets (customer_id, created_at DESC);
CREATE INDEX ix_ticket_status_created ON warranty_tickets (status, created_at);

-- Bảng lưu bộ đếm số thứ tự sinh mã ticket theo tháng dạng TCK-WAR-YYYYMM-XXXX
CREATE TABLE warranty_ticket_code_counters (
  year_month CHAR(6) PRIMARY KEY,
  last_seq   INT NOT NULL
);

