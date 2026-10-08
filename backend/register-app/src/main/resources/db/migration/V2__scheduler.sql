-- Job storage for db-scheduler (https://github.com/kagkarlsson/db-scheduler), PostgreSQL schema from its README.

CREATE TABLE register.scheduled_tasks (
    task_name            text                     NOT NULL,
    task_instance        text                     NOT NULL,
    task_data            bytea,
    execution_time       timestamp with time zone NOT NULL,
    picked               boolean                  NOT NULL,
    picked_by            text,
    last_success         timestamp with time zone,
    last_failure         timestamp with time zone,
    consecutive_failures integer,
    last_heartbeat       timestamp with time zone,
    version              bigint                   NOT NULL,
    priority             smallint,
    PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX scheduled_tasks_execution_time_idx ON register.scheduled_tasks (execution_time);
CREATE INDEX scheduled_tasks_last_heartbeat_idx ON register.scheduled_tasks (last_heartbeat);
CREATE INDEX scheduled_tasks_priority_execution_time_idx ON register.scheduled_tasks (priority DESC, execution_time ASC);

GRANT SELECT, INSERT, UPDATE, DELETE ON register.scheduled_tasks TO register_app;
