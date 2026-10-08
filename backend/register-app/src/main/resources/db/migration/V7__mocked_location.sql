-- Field captures whose location the device reported as mocked (Android's mocked-location flag). Accepted, never
-- blocked, and shown to the approver.

ALTER TABLE register.change_request ADD COLUMN location_mocked boolean NOT NULL DEFAULT false;
ALTER TABLE register.change_request_history ADD COLUMN location_mocked boolean;
