-- "Return to the proposer with a written reason" (approval screen). A new enum value cannot be used in the
-- transaction that adds it, so the constraints that mention it follow in V5.
ALTER TYPE register.change_state ADD VALUE IF NOT EXISTS 'returned' AFTER 'rejected';
