-- Decisions on change requests: approve, or return with a written reason (four-eyes rule, approval screen).

SET search_path = register, public;

-- History rows used to be written by position (SELECT ($1).*, ...). A column added to a table later would then land
-- in the wrong history column, because the history twin appends its own columns first. Name every column instead.
CREATE OR REPLACE FUNCTION register.record_history() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = register, public AS
$$
DECLARE
    row_ record;
    columns_ text;
BEGIN
    IF TG_OP = 'DELETE' THEN
        row_ := OLD;
    ELSE
        row_ := NEW;
    END IF;
    SELECT string_agg(quote_ident(a.attname), ', ' ORDER BY a.attnum) INTO columns_
      FROM pg_attribute a
     WHERE a.attrelid = TG_RELID AND a.attnum > 0 AND NOT a.attisdropped;
    EXECUTE format('INSERT INTO %I.%I (%s, history_operation, history_recorded_at, history_txid, history_actor)
                    SELECT %s, $2, clock_timestamp(), pg_current_xact_id()::text::bigint, $3 FROM (SELECT ($1).*) AS r',
                   TG_TABLE_SCHEMA, TG_TABLE_NAME || '_history', columns_, columns_)
        USING row_, lower(TG_OP), nullif(current_setting('app.subject', true), '');
    RETURN NULL;
END;
$$;
REVOKE EXECUTE ON FUNCTION register.record_history() FROM PUBLIC;

ALTER TABLE register.change_request ADD COLUMN decision_reason text;
ALTER TABLE register.change_request_history ADD COLUMN decision_reason text;

ALTER TABLE register.change_request DROP CONSTRAINT change_request_decision;
ALTER TABLE register.change_request ADD CONSTRAINT change_request_decision CHECK (
    (state IN ('approved', 'rejected', 'returned', 'applied')) = (decided_by IS NOT NULL AND decided_at IS NOT NULL));
ALTER TABLE register.change_request ADD CONSTRAINT change_request_return_reason CHECK (
    state <> 'returned' OR length(decision_reason) BETWEEN 3 AND 500);

-- The approver inbox lists a jurisdiction's requests by state, oldest first.
CREATE INDEX change_request_inbox_idx ON register.change_request (state, created_at, id);
