-- Spring Batch metadata schema (PostgreSQL)
-- Compatible with Spring Batch 5.x defaults (Spring Boot 3.x).
-- Run once in your database (e.g., Supabase SQL Editor).

-- Sequences
CREATE SEQUENCE IF NOT EXISTS batch_job_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS batch_job_execution_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS batch_step_execution_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

-- Job instance
CREATE TABLE IF NOT EXISTS batch_job_instance (
  job_instance_id BIGINT PRIMARY KEY,
  version BIGINT,
  job_name VARCHAR(100) NOT NULL,
  job_key VARCHAR(32) NOT NULL,
  CONSTRAINT uk_batch_job_instance UNIQUE (job_name, job_key)
);

-- Job execution
CREATE TABLE IF NOT EXISTS batch_job_execution (
  job_execution_id BIGINT PRIMARY KEY,
  version BIGINT,
  job_instance_id BIGINT NOT NULL,
  create_time TIMESTAMP NOT NULL,
  start_time TIMESTAMP,
  end_time TIMESTAMP,
  status VARCHAR(10),
  exit_code VARCHAR(2500),
  exit_message VARCHAR(2500),
  last_updated TIMESTAMP,
  CONSTRAINT fk_batch_job_execution_instance FOREIGN KEY (job_instance_id)
    REFERENCES batch_job_instance(job_instance_id)
);

CREATE INDEX IF NOT EXISTS idx_batch_job_execution_instance ON batch_job_execution(job_instance_id);

-- Job execution params
CREATE TABLE IF NOT EXISTS batch_job_execution_params (
  job_execution_id BIGINT NOT NULL,
  parameter_name VARCHAR(100) NOT NULL,
  parameter_type VARCHAR(100) NOT NULL,
  parameter_value VARCHAR(2500),
  identifying CHAR(1) NOT NULL,
  CONSTRAINT fk_batch_job_execution_params_execution FOREIGN KEY (job_execution_id)
    REFERENCES batch_job_execution(job_execution_id)
);

CREATE INDEX IF NOT EXISTS idx_batch_job_execution_params_execution ON batch_job_execution_params(job_execution_id);

-- Step execution
CREATE TABLE IF NOT EXISTS batch_step_execution (
  step_execution_id BIGINT PRIMARY KEY,
  version BIGINT NOT NULL,
  step_name VARCHAR(100) NOT NULL,
  job_execution_id BIGINT NOT NULL,
  create_time TIMESTAMP NOT NULL,
  start_time TIMESTAMP,
  end_time TIMESTAMP,
  status VARCHAR(10),
  commit_count BIGINT,
  read_count BIGINT,
  filter_count BIGINT,
  write_count BIGINT,
  read_skip_count BIGINT,
  write_skip_count BIGINT,
  process_skip_count BIGINT,
  rollback_count BIGINT,
  exit_code VARCHAR(2500),
  exit_message VARCHAR(2500),
  last_updated TIMESTAMP,
  CONSTRAINT fk_batch_step_execution_job_execution FOREIGN KEY (job_execution_id)
    REFERENCES batch_job_execution(job_execution_id)
);

CREATE INDEX IF NOT EXISTS idx_batch_step_execution_job_execution ON batch_step_execution(job_execution_id);

-- Execution contexts
CREATE TABLE IF NOT EXISTS batch_job_execution_context (
  job_execution_id BIGINT PRIMARY KEY,
  short_context VARCHAR(2500) NOT NULL,
  serialized_context TEXT,
  CONSTRAINT fk_batch_job_execution_context_execution FOREIGN KEY (job_execution_id)
    REFERENCES batch_job_execution(job_execution_id)
);

CREATE TABLE IF NOT EXISTS batch_step_execution_context (
  step_execution_id BIGINT PRIMARY KEY,
  short_context VARCHAR(2500) NOT NULL,
  serialized_context TEXT,
  CONSTRAINT fk_batch_step_execution_context_execution FOREIGN KEY (step_execution_id)
    REFERENCES batch_step_execution(step_execution_id)
);

