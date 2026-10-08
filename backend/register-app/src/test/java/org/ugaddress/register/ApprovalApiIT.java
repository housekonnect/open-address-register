package org.ugaddress.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.ugaddress.register.shared.Role;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The approver inbox and decisions: four-eyes rule, jurisdiction, written reasons, evidence and audit events.
 */
class ApprovalApiIT extends AbstractIntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private JsonMapper json;

    private static RequestPostProcessor user(final String subject, final String custodian, final Role... roles) {
        final List<String> groups = Arrays.stream(roles).map(Role::groupName).toList();
        return jwt().jwt(j -> j.subject(subject).claim("custodian", custodian).claim("groups", groups))
            .authorities(Arrays.stream(roles).<GrantedAuthority>map(r -> new SimpleGrantedAuthority(r.authority())).toList());
    }

    /** Proposes and may approve (both groups), like the `approver` test user. */
    private static RequestPostProcessor editorApprover(final String subject) {
        return user(subject, "demo-city", Role.CUSTODIAN_EDITOR, Role.CUSTODIAN_APPROVER);
    }

    private static RequestPostProcessor approver(final String subject) {
        return user(subject, "demo-city", Role.CUSTODIAN_APPROVER);
    }

    private UUID objectIdOfHouse(final String street, final int number) {
        return dsl.fetchSingle("""
                select a.object_id from register.address a
                  join register.thoroughfare t on t.id = a.thoroughfare_id
                 where t.name = ? and a.house_number = ?""", street, Integer.toString(number))
            .get(0, UUID.class);
    }

    private UUID propose(final RequestPostProcessor proposer, final int house, final String houseNumber)
        throws Exception {
        final String body = """
            {"kind": "correction", "targetObjectId": "%s", "summary": "The plate shows %s",
             "proposedHouseNumber": "%s"}""".formatted(objectIdOfHouse("Amani Avenue", house), houseNumber,
            houseNumber);
        final JsonNode created = json.readTree(mvc.perform(post("/v1/change-requests")
                .header("Idempotency-Key", "propose-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(body).with(proposer))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return UUID.fromString(created.get("id").asString());
    }

    private int auditEvents(final UUID changeRequestId, final String action) {
        return dsl.fetchCount(dsl.selectFrom("register.audit_event")
            .where("entity_id = ? and action = ?", changeRequestId, action));
    }

    @Test
    void theProposerCannotApproveTheirOwnRequest() throws Exception {
        // GIVEN a change request proposed by someone who is also an approver
        final UUID id = propose(editorApprover("both-roles-1"), 3, "3A");

        // WHEN they open it, and try to approve it
        mvc.perform(get("/v1/change-requests/{id}", id).with(editorApprover("both-roles-1")))
            // THEN the server says they may not decide it, because it is their own
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions.decide").value(false))
            .andExpect(jsonPath("$.permissions.reason").value("own_request"));
        mvc.perform(post("/v1/change-requests/{id}/approve", id).header("Idempotency-Key", "self-approve-1")
                .with(editorApprover("both-roles-1")))
            // AND approving answers 403 as a problem detail, leaving the request undecided and unaudited
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(
                "Four-eyes rule: the proposer cannot decide their own change request."));
        assertThat(dsl.fetchSingle("select state::text from register.change_request where id = ?", id)
            .get(0, String.class)).isEqualTo("submitted");
        assertThat(auditEvents(id, "change_request.approved")).isZero();
    }

    @Test
    void anotherApproverApprovesOnceAndTheDecisionIsAudited() throws Exception {
        // GIVEN a change request proposed by an editor
        final UUID id = propose(user("editor-a", "demo-city", Role.CUSTODIAN_EDITOR), 4, "4B");

        // WHEN another approver approves it, and the request is retried with the same key
        mvc.perform(post("/v1/change-requests/{id}/approve", id).header("Idempotency-Key", "approve-0001")
                .with(approver("approver-a")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("approved"))
            .andExpect(jsonPath("$.decidedAt").exists())
            .andExpect(jsonPath("$.permissions.reason").value("already_decided"));
        mvc.perform(post("/v1/change-requests/{id}/approve", id).header("Idempotency-Key", "approve-0001")
                .with(approver("approver-a")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("approved"));

        // THEN exactly one audit event records the decision, and a new decision is a conflict
        assertThat(auditEvents(id, "change_request.approved")).isEqualTo(1);
        mvc.perform(post("/v1/change-requests/{id}/approve", id).header("Idempotency-Key", "approve-0002")
                .with(approver("approver-b")))
            .andExpect(status().isConflict());
    }

    @Test
    void returningNeedsAWrittenReasonWhichIsStoredAndAudited() throws Exception {
        // GIVEN a change request
        final UUID id = propose(user("editor-b", "demo-city", Role.CUSTODIAN_EDITOR), 5, "5C");

        // WHEN an approver returns it without a reason THEN that is a bad request
        mvc.perform(post("/v1/change-requests/{id}/return", id).header("Idempotency-Key", "return-0001")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"  \"}").with(approver("approver-c")))
            .andExpect(status().isBadRequest());

        // WHEN it is returned with a reason
        mvc.perform(post("/v1/change-requests/{id}/return", id).header("Idempotency-Key", "return-0002")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"The photo does not show the plate. Please retake it.\"}")
                .with(approver("approver-c")))
            // THEN it is returned with the reason, and the audit event carries it
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("returned"))
            .andExpect(jsonPath("$.decisionReason").value("The photo does not show the plate. Please retake it."));
        assertThat(dsl.fetchSingle("""
                select payload->>'reason' from register.audit_event
                 where entity_id = ? and action = 'change_request.returned'""", id).get(0, String.class))
            .isEqualTo("The photo does not show the plate. Please retake it.");
        // AND the history twin recorded the decision in the right column
        assertThat(dsl.fetchSingle("""
                select decision_reason, history_operation from register.change_request_history
                 where id = ? order by history_seq desc limit 1""", id).intoArray())
            .containsExactly("The photo does not show the plate. Please retake it.", "update");
    }

    @Test
    void theInboxShowsTheJurisdictionsRequestsWithDiffAndPermissions() throws Exception {
        // GIVEN a pending correction of 6 Amani Avenue's house number
        final UUID id = propose(user("editor-c", "demo-city", Role.CUSTODIAN_EDITOR), 7, "7D");

        // WHEN an approver of demo-city opens the inbox of submitted requests
        final JsonNode page = json.readTree(mvc.perform(get("/v1/change-requests").param("state", "submitted")
                .param("limit", "100").with(approver("approver-d")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // THEN the request is listed with its target, its diff and the right to decide it
        final JsonNode item = page.get("items").valueStream()
            .filter(i -> i.get("id").asString().equals(id.toString())).findFirst().orElseThrow();
        assertThat(item.at("/target/label").asString()).isEqualTo("7 Amani Avenue");
        assertThat(item.at("/target/location/type").asString()).isEqualTo("Point");
        assertThat(item.at("/diff/0/field").asString()).isEqualTo("houseNumber");
        assertThat(item.at("/diff/0/current").asString()).isEqualTo("7");
        assertThat(item.at("/diff/0/proposed").asString()).isEqualTo("7D");
        assertThat(item.at("/permissions/decide").asBoolean()).isTrue();
        assertThat(page.get("items").valueStream().map(i -> i.get("state").asString())).containsOnly("submitted");
    }

    @Test
    void onlyApproversOfTheJurisdictionMaySeeAndDecide() throws Exception {
        // GIVEN a change request in Amani parish (demo-city)
        final UUID id = propose(user("editor-d", "demo-city", Role.CUSTODIAN_EDITOR), 8, "8E");

        // WHEN an editor opens the inbox THEN 403
        mvc.perform(get("/v1/change-requests").with(user("editor-d", "demo-city", Role.CUSTODIAN_EDITOR)))
            .andExpect(status().isForbidden());
        // WHEN an approver of another custodian (demo-district: Mirembe parish) opens or approves it THEN 403
        final RequestPostProcessor otherApprover = user("approver-e", "demo-district", Role.CUSTODIAN_APPROVER);
        mvc.perform(get("/v1/change-requests/{id}", id).with(otherApprover))
            .andExpect(status().isForbidden());
        mvc.perform(post("/v1/change-requests/{id}/approve", id).header("Idempotency-Key", "approve-other-1")
                .with(otherApprover))
            .andExpect(status().isForbidden());
        // AND their inbox does not list it
        final String inbox = mvc.perform(get("/v1/change-requests").param("limit", "100").with(otherApprover))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(inbox).doesNotContain(id.toString());
    }

    @Test
    void approversSeeTheEvidencePhotoOfAFieldCapture() throws Exception {
        // GIVEN a field capture with a photo
        final byte[] photo = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 9, 8, 7, (byte) 0xFF, (byte) 0xD9};
        final MockMultipartFile metadata = new MockMultipartFile("metadata", "", "application/json", """
            {"capturedAt": "2026-10-08T10:00:00Z", "kind": "building",
             "location": {"type": "Point", "coordinates": [32.5951, 0.3504]}, "photoSha256": "%s"}"""
            .formatted(TestPhotos.sha256(photo)).getBytes());
        final JsonNode capture = json.readTree(mvc.perform(multipart("/v1/field/captures").file(metadata)
                .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", photo))
                .header("Idempotency-Key", "evidence-0001").with(user("verifier-e", "demo-city", Role.FIELD_VERIFIER)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        final String id = capture.get("changeRequestId").asString();

        // WHEN an approver opens the request and its photo
        mvc.perform(get("/v1/change-requests/{id}", id).with(approver("approver-f")))
            // THEN the evidence lists the photo and the captured point
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidence.photo").value(true))
            .andExpect(jsonPath("$.evidence.photoSha256").value(TestPhotos.sha256(photo)))
            .andExpect(jsonPath("$.evidence.location.coordinates[0]").value(32.5951))
            .andExpect(jsonPath("$.target.type").value("location"));
        mvc.perform(get("/v1/change-requests/{id}/photo", id).with(approver("approver-f")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(content().bytes(photo));
        // AND the verifier, who is not an approver, cannot fetch it
        mvc.perform(get("/v1/change-requests/{id}/photo", id).with(user("verifier-e", "demo-city", Role.FIELD_VERIFIER)))
            .andExpect(status().isForbidden());
    }

    @Test
    void aCaptureWithAMockedLocationIsAcceptedAndFlaggedForTheApprover() throws Exception {
        // GIVEN a capture whose location the device reported as mocked (Android's mocked-location flag)
        final byte[] photo = TestPhotos.jpeg(61);
        final MockMultipartFile metadata = new MockMultipartFile("metadata", "", "application/json", """
            {"capturedAt": "2026-10-08T12:00:00Z", "kind": "building", "locationMocked": true,
             "location": {"type": "Point", "coordinates": [32.5952, 0.3505]}, "photoSha256": "%s"}"""
            .formatted(TestPhotos.sha256(photo)).getBytes());

        // WHEN it is uploaded
        final JsonNode capture = json.readTree(mvc.perform(multipart("/v1/field/captures").file(metadata)
                .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", photo))
                .header("Idempotency-Key", "mocked-0001").with(user("verifier-g", "demo-city", Role.FIELD_VERIFIER)))
            // THEN it is accepted, not blocked
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        final String id = capture.get("changeRequestId").asString();

        // AND the change request is flagged, for the approver and in the audit event
        mvc.perform(get("/v1/change-requests/{id}", id).with(approver("approver-g")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidence.locationMocked").value(true));
        assertThat(dsl.fetchSingle("""
                select payload->>'locationMocked' from register.audit_event
                 where entity_id = ?::uuid and action = 'field.photo_stored'""", id).get(0, String.class))
            .isEqualTo("true");
    }

    @Test
    void capturesWithoutTheFlagAreNotMarkedMocked() throws Exception {
        // GIVEN a console correction (no device location at all)
        final UUID id = propose(user("editor-h", "demo-city", Role.CUSTODIAN_EDITOR), 9, "9F");
        // WHEN an approver opens it THEN it is not flagged
        mvc.perform(get("/v1/change-requests/{id}", id).with(approver("approver-h")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidence.locationMocked").value(false));
    }
}
