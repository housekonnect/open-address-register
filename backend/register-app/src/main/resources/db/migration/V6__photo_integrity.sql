-- Photo integrity: the SHA-256 the field app computed on the device, verified by the backend against the stored
-- object before the capture is accepted.

ALTER TABLE register.change_request ADD COLUMN photo_sha256 text;
ALTER TABLE register.change_request_history ADD COLUMN photo_sha256 text;
ALTER TABLE register.change_request ADD CONSTRAINT change_request_photo_sha256_format CHECK (
    photo_sha256 IS NULL OR (photo_sha256 ~ '^[0-9a-f]{64}$' AND photo_object_key IS NOT NULL));
