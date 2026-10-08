package org.ugaddress.register.shared;

import static org.ugaddress.db.generated.Tables.IDEMPOTENCY_KEY;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.ugaddress.db.generated.tables.records.IdempotencyKeyRecord;

/**
 * Makes POST operations safe to retry.
 *
 * <p>Call {@link #claim} and {@link #complete} in the same transaction as the operation itself. A concurrent request
 * with the same key waits on the key's row until the first transaction ends, then sees its result.
 */
@Service
public class IdempotencyService {

    private final DSLContext dsl;

    /**
     * Creates the service.
     *
     * @param dsl jOOQ context
     */
    public IdempotencyService(final DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    /**
     * Claims a key for an operation.
     *
     * @param request the idempotent request
     * @return empty if the caller should perform the operation now, or the id of the resource created by an earlier
     *     identical request
     * @throws ProblemException with status 422 if the key was used for a different request
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> claim(final IdempotentRequest request) {
        final int inserted = dsl.insertInto(IDEMPOTENCY_KEY)
            .set(IDEMPOTENCY_KEY.PRINCIPAL, request.principal())
            .set(IDEMPOTENCY_KEY.IDEMPOTENCY_KEY_, request.key())
            .set(IDEMPOTENCY_KEY.OPERATION, request.operation())
            .set(IDEMPOTENCY_KEY.REQUEST_HASH, request.fingerprint())
            .onConflictDoNothing()
            .execute();
        if (inserted == 1) {
            return Optional.empty();
        }
        final IdempotencyKeyRecord existing = dsl.selectFrom(IDEMPOTENCY_KEY)
            .where(IDEMPOTENCY_KEY.PRINCIPAL.eq(request.principal()))
            .and(IDEMPOTENCY_KEY.IDEMPOTENCY_KEY_.eq(request.key()))
            .fetchSingle();
        if (!existing.getOperation().equals(request.operation())
            || !Arrays.equals(existing.getRequestHash(), request.fingerprint())
            || existing.getResourceId() == null) {
            throw ProblemException.idempotencyConflict();
        }
        return Optional.of(existing.getResourceId());
    }

    /**
     * Records the result of a claimed operation.
     *
     * @param request the request passed to {@link #claim}
     * @param resourceId id of the created resource
     * @param status HTTP status of the original response
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(final IdempotentRequest request, final UUID resourceId, final int status) {
        dsl.update(IDEMPOTENCY_KEY)
            .set(IDEMPOTENCY_KEY.RESOURCE_ID, resourceId)
            .set(IDEMPOTENCY_KEY.RESPONSE_STATUS, status)
            .where(IDEMPOTENCY_KEY.PRINCIPAL.eq(request.principal()))
            .and(IDEMPOTENCY_KEY.IDEMPOTENCY_KEY_.eq(request.key()))
            .execute();
    }

    /**
     * Computes a SHA-256 fingerprint over request parts.
     *
     * @param parts serialized request parts, in a fixed order
     * @return the fingerprint
     */
    public static byte[] fingerprint(final byte[]... parts) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (final byte[] part : parts) {
                digest.update(Integer.toString(part.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(part);
            }
            return digest.digest();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    /**
     * An idempotent request.
     *
     * @param principal subject of the caller; keys are scoped per caller
     * @param key the client's {@code Idempotency-Key}
     * @param operation operation id from the contract
     * @param fingerprint fingerprint of the request body, see {@link #fingerprint(byte[]...)}
     */
    public record IdempotentRequest(String principal, String key, String operation, byte[] fingerprint) {

        /**
         * Creates the request.
         *
         * @param principal subject of the caller
         * @param key the client's key
         * @param operation operation id
         * @param fingerprint request fingerprint
         */
        public IdempotentRequest {
            Objects.requireNonNull(principal, "principal");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(operation, "operation");
            fingerprint = fingerprint.clone();
        }

        @Override
        public byte[] fingerprint() {
            return fingerprint.clone();
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof final IdempotentRequest that
                && principal.equals(that.principal) && key.equals(that.key) && operation.equals(that.operation)
                && Arrays.equals(fingerprint, that.fingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(principal, key, operation, Arrays.hashCode(fingerprint));
        }

        @Override
        public String toString() {
            return "IdempotentRequest[operation=" + operation + "]";
        }
    }
}
