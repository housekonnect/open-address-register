package org.ugaddress.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.ugaddress.register.field.internal.PhotoRepository;
import org.ugaddress.register.shared.IdempotencyService;
import org.ugaddress.register.shared.Role;
import tools.jackson.databind.json.JsonMapper;

/**
 * Photo integrity: the backend re-hashes the stored object and compares it with the device's SHA-256.
 */
class PhotoIntegrityIT extends AbstractIntegrationTest {

    private static final String VERIFIER = "verifier-integrity";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private JsonMapper json;

    @Autowired
    private PhotoRepository photos;

    private static RequestPostProcessor verifier() {
        return jwt().jwt(j -> j.subject(VERIFIER).claim("custodian", "demo-city")
                .claim("groups", List.of(Role.FIELD_VERIFIER.groupName())))
            .authorities(new SimpleGrantedAuthority(Role.FIELD_VERIFIER.authority()));
    }

    private static MockMultipartFile metadata(final String photoSha256) {
        return new MockMultipartFile("metadata", "", "application/json", """
            {"capturedAt": "2026-10-08T11:00:00Z", "kind": "building",
             "location": {"type": "Point", "coordinates": [32.5948, 0.3503]}, "photoSha256": "%s"}"""
            .formatted(photoSha256).getBytes(StandardCharsets.UTF_8));
    }

    /** The object key the backend derives for a capture (see FieldCaptureService). */
    private static String photoKey(final String idempotencyKey) {
        return "captures/" + HexFormat.of().formatHex(IdempotencyService.fingerprint(
            VERIFIER.getBytes(StandardCharsets.UTF_8), idempotencyKey.getBytes(StandardCharsets.UTF_8))) + ".jpg";
    }

    private int count(final String sql, final Object... bindings) {
        return dsl.fetchSingle(sql, bindings).get(0, Integer.class);
    }

    @Test
    void aMismatchIsRejectedWith422AndNothingIsKept() throws Exception {
        // GIVEN a photo and the hash of a different photo, as if the bytes changed after the device hashed them
        final byte[] photo = TestPhotos.jpeg(41);
        final String wrongHash = TestPhotos.sha256(TestPhotos.jpeg(42));
        final String key = "integrity-mismatch-0001";
        final int auditBefore = count("select count(*) from register.audit_event where actor = ?", VERIFIER);

        // WHEN the capture is uploaded
        mvc.perform(multipart("/v1/field/captures").file(metadata(wrongHash))
                .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", photo))
                .header("Idempotency-Key", key).with(verifier()))
            // THEN it is rejected as a problem detail
            .andExpect(status().isUnprocessableContent())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.title").value("Photo integrity check failed"));

        // AND nothing was kept: no object, no change request, no idempotency claim, no audit event
        assertThat(photos.exists(photoKey(key))).isFalse();
        assertThat(count("select count(*) from register.change_request where photo_object_key = ?", photoKey(key)))
            .isZero();
        assertThat(count("select count(*) from register.idempotency_key where idempotency_key = ?", key)).isZero();
        assertThat(count("select count(*) from register.audit_event where actor = ?", VERIFIER))
            .isEqualTo(auditBefore);

        // AND a retry with the same key and the right hash succeeds, because nothing was claimed
        mvc.perform(multipart("/v1/field/captures").file(metadata(TestPhotos.sha256(photo)))
                .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", photo))
                .header("Idempotency-Key", key).with(verifier()))
            .andExpect(status().isCreated());
    }

    @Test
    void aMatchingHashIsKeptWithTheEvidenceAndInTheAuditEvent() throws Exception {
        // GIVEN a photo and its hash computed on the device
        final byte[] photo = TestPhotos.jpeg(43);
        final String hash = TestPhotos.sha256(photo);

        // WHEN the capture is uploaded
        final String body = mvc.perform(multipart("/v1/field/captures").file(metadata(hash))
                .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", photo))
                .header("Idempotency-Key", "integrity-match-0001").with(verifier()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        final UUID changeRequestId = UUID.fromString(json.readTree(body).get("changeRequestId").asString());

        // THEN the change request keeps the verified hash, and the audit event of the stored photo carries it
        assertThat(dsl.fetchSingle("select photo_sha256 from register.change_request where id = ?", changeRequestId)
            .get(0, String.class)).isEqualTo(hash);
        assertThat(dsl.fetchSingle("""
                select payload->>'sha256' from register.audit_event
                 where entity_id = ? and action = 'field.photo_stored'""", changeRequestId).get(0, String.class))
            .isEqualTo(hash);
    }

    @Test
    void aMissingOrMalformedHashIsABadRequest() throws Exception {
        // GIVEN / WHEN a capture without a valid SHA-256 is uploaded THEN 400
        mvc.perform(multipart("/v1/field/captures").file(metadata("not-a-hash"))
                .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", TestPhotos.jpeg(44)))
                .header("Idempotency-Key", "integrity-bad-0001").with(verifier()))
            .andExpect(status().isBadRequest());
    }
}
