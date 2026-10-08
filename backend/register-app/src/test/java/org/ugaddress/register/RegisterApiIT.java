package org.ugaddress.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.ugaddress.register.field.internal.PhotoRepository;
import org.ugaddress.register.shared.CurrentActor;
import org.ugaddress.register.shared.ProblemException;
import org.ugaddress.register.shared.Role;
import org.ugaddress.register.workflow.ChangeRequestDTO;
import org.ugaddress.register.workflow.ChangeRequestService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The implemented API slice end to end: HTTP → security → modules → PostGIS / Record Store.
 */
class RegisterApiIT extends AbstractIntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private JsonMapper json;

    @Autowired
    private ChangeRequestService changeRequests;

    @Autowired
    private PhotoRepository photos;

    private static RequestPostProcessor user(final String subject, final String custodian, final Role... roles) {
        final List<String> groups = Arrays.stream(roles).map(Role::groupName).toList();
        return jwt().jwt(j -> j.subject(subject).claim("custodian", custodian).claim("groups", groups))
            .authorities(Arrays.stream(roles).<GrantedAuthority>map(r -> new SimpleGrantedAuthority(r.authority())).toList());
    }

    private static RequestPostProcessor partner() {
        return jwt().jwt(j -> j.subject("partner-1"))
            .authorities(new SimpleGrantedAuthority("SCOPE_" + CurrentActor.PARTNER_SCOPE));
    }

    private String nationalIdOfHouse(final String street, final int number) {
        return dsl.fetchSingle("""
                select o.national_id from register.addressable_object o
                  join register.address a on a.object_id = o.id
                  join register.thoroughfare t on t.id = a.thoroughfare_id
                 where t.name = ? and a.house_number = ?""", street, Integer.toString(number))
            .get(0, String.class).trim();
    }

    private UUID objectIdOfHouse(final String street, final int number) {
        return dsl.fetchSingle("""
                select a.object_id from register.address a
                  join register.thoroughfare t on t.id = a.thoroughfare_id
                 where t.name = ? and a.house_number = ?""", street, Integer.toString(number))
            .get(0, UUID.class);
    }

    private JsonNode body(final MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void resolvesANationalIdAndHidesResidentialEntranceCoordinates() throws Exception {
        // GIVEN a residential building in the fixtures
        final String nationalId = nationalIdOfHouse("Amani Avenue", 1);
        final String displayed = nationalId.substring(0, 4) + " " + nationalId.substring(4, 7) + " "
            + nationalId.substring(7);

        // WHEN it is resolved anonymously by its display form
        mvc.perform(get("/v1/resolve").param("ref", displayed))
            // THEN the address is returned, marked DEMO, without residential entrance coordinates
            .andExpect(status().isOk())
            .andExpect(header().exists("ETag"))
            .andExpect(jsonPath("$.matchedBy").value("national_id"))
            .andExpect(jsonPath("$.object.displayId").value("DEMO " + displayed))
            .andExpect(jsonPath("$.object.nationalIdStatus").value("demonstration"))
            .andExpect(jsonPath("$.object.address.lines[0]").value("1 Amani Avenue"))
            .andExpect(jsonPath("$.object.entrances[0].residential").value(true))
            .andExpect(jsonPath("$.object.entrances[0].location").doesNotExist());
    }

    @Test
    void partnersSeeResidentialEntranceCoordinates() throws Exception {
        // GIVEN a residential building
        final String nationalId = nationalIdOfHouse("Amani Avenue", 1);

        // WHEN a partner resolves it
        mvc.perform(get("/v1/resolve").param("ref", nationalId).with(partner()))
            // THEN the entrance location is included
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.object.entrances[0].location.type").value("Point"));
    }

    @Test
    void resolvesAnAlias() throws Exception {
        // GIVEN the alias of house 3 on Jacaranda Close
        // WHEN it is resolved THEN it matches by alias
        mvc.perform(get("/v1/resolve").param("ref", "demo-plot:JAC-0003"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matchedBy").value("alias"))
            .andExpect(jsonPath("$.object.address.lines[0]").value("3 Jacaranda Close"));
    }

    @Test
    void rejectsMistypedIdsAndReportsUnknownOnes() throws Exception {
        // GIVEN a valid ID with one digit changed
        final String nationalId = nationalIdOfHouse("Amani Avenue", 2);
        final char last = nationalId.charAt(10);
        final String typo = nationalId.substring(0, 10) + (char) (last == '9' ? '0' : last + 1);

        // WHEN it is resolved THEN the check digit catches it
        mvc.perform(get("/v1/resolve").param("ref", typo))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid national ID"));

        // WHEN an unknown alias is resolved THEN 404 as a problem detail
        mvc.perform(get("/v1/resolve").param("ref", "demo-plot:NOPE-9999"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void objectsSupportEtagsAndHistory() throws Exception {
        // GIVEN an object
        final UUID id = objectIdOfHouse("Mirembe Road", 4);

        // WHEN it is fetched THEN an ETag is returned
        final String etag = mvc.perform(get("/v1/objects/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andReturn().getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();

        // WHEN it is fetched again with If-None-Match THEN 304
        mvc.perform(get("/v1/objects/{id}", id).header("If-None-Match", etag))
            .andExpect(status().isNotModified());

        // WHEN its history is fetched THEN the insert from the fixtures is there
        mvc.perform(get("/v1/objects/{id}/history", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].operation").value("insert"));
    }

    @Test
    void contractedButUnimplementedOperationsReturn501() throws Exception {
        mvc.perform(get("/v1/search").param("q", "Amani"))
            .andExpect(status().isNotImplemented())
            .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        mvc.perform(get("/v1/reverse").param("lat", "0.35").param("lon", "32.59"))
            .andExpect(status().isNotImplemented());
        mvc.perform(get("/v1/changes").param("since", "2026-01-01T00:00:00Z"))
            .andExpect(status().isNotImplemented());
        mvc.perform(get("/v1/field/assignments").with(user("verifier-1", "demo-city", Role.FIELD_VERIFIER)))
            .andExpect(status().isNotImplemented());
    }

    @Test
    void changeRequestsRequireAnEditorToken() throws Exception {
        final String body = """
            {"kind": "correction", "summary": "Plate shows a different number"}""";
        // WHEN no token is sent THEN 401 as a problem detail
        mvc.perform(post("/v1/change-requests").header("Idempotency-Key", "anon-key-0001")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        // WHEN the request carries a cookie (ambient credential) but no bearer token THEN CSRF protection rejects it
        mvc.perform(post("/v1/change-requests").header("Idempotency-Key", "cookie-key-0001")
                .cookie(new Cookie("session", "abc"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        // WHEN a field verifier sends one THEN 403
        mvc.perform(post("/v1/change-requests").header("Idempotency-Key", "verifier-key-0001")
                .contentType(MediaType.APPLICATION_JSON).content(body)
                .with(user("verifier-1", "demo-city", Role.FIELD_VERIFIER)))
            .andExpect(status().isForbidden());
    }

    @Test
    void submittingACorrectionIsIdempotentAndAudited() throws Exception {
        // GIVEN an editor of demo-city and an object in Amani parish
        final UUID target = objectIdOfHouse("Amani Avenue", 5);
        final String body = """
            {"kind": "correction", "targetObjectId": "%s", "summary": "House plate shows 5A", "proposedHouseNumber": "5A"}
            """.formatted(target);
        final long auditBefore = dsl.fetchCount(dsl.selectFrom("register.audit_event")
            .where("action = 'change_request.submitted'"));

        // WHEN the correction is submitted twice with the same key
        final MvcResult first = mvc.perform(post("/v1/change-requests").header("Idempotency-Key", "editor-key-0001")
                .contentType(MediaType.APPLICATION_JSON).content(body)
                .with(user("editor-1", "demo-city", Role.CUSTODIAN_EDITOR)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.state").value("submitted"))
            .andExpect(jsonPath("$.source").value("console"))
            .andReturn();
        final MvcResult second = mvc.perform(post("/v1/change-requests").header("Idempotency-Key", "editor-key-0001")
                .contentType(MediaType.APPLICATION_JSON).content(body)
                .with(user("editor-1", "demo-city", Role.CUSTODIAN_EDITOR)))
            .andExpect(status().isCreated())
            .andReturn();

        // THEN both responses carry the same change request, stored once, with one audit event
        final String id = body(first).get("id").asString();
        assertThat(body(second).get("id").asString()).isEqualTo(id);
        assertThat(dsl.fetchCount(dsl.selectFrom("register.change_request").where("id = ?::uuid", id))).isEqualTo(1);
        assertThat(dsl.fetchCount(dsl.selectFrom("register.audit_event")
            .where("action = 'change_request.submitted'"))).isEqualTo(auditBefore + 1);

        // WHEN the key is reused with a different body THEN 422
        mvc.perform(post("/v1/change-requests").header("Idempotency-Key", "editor-key-0001")
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("5A", "5B"))
                .with(user("editor-1", "demo-city", Role.CUSTODIAN_EDITOR)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void editorsCannotChangeAnotherCustodiansArea() throws Exception {
        // GIVEN an object in Mirembe parish (demo-district) and an editor of demo-city
        final UUID target = objectIdOfHouse("Mirembe Road", 2);

        // WHEN the editor submits a correction THEN 403
        mvc.perform(post("/v1/change-requests").header("Idempotency-Key", "editor-key-0002")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"kind": "correction", "targetObjectId": "%s", "summary": "Not my parish"}""".formatted(target))
                .with(user("editor-1", "demo-city", Role.CUSTODIAN_EDITOR)))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void fieldCaptureStoresThePhotoAndRetriesDoNotDuplicate() throws Exception {
        // GIVEN a capture made offline in Amani parish with a client-generated key
        final MockMultipartFile metadata = new MockMultipartFile("metadata", "", "application/json", """
            {"capturedAt": "2026-10-08T09:30:00Z", "kind": "building",
             "location": {"type": "Point", "coordinates": [32.5945, 0.3502]}, "note": "New building, no plate yet"}
            """.getBytes());
        final MockMultipartFile photo = new MockMultipartFile("photo", "capture.jpg", "image/jpeg",
            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4, (byte) 0xFF, (byte) 0xD9});
        final RequestPostProcessor verifier = user("verifier-1", "demo-city", Role.FIELD_VERIFIER);

        // WHEN it is synced, and the sync is retried with the same key
        final MvcResult first = mvc.perform(multipart("/v1/field/captures").file(metadata).file(photo)
                .header("Idempotency-Key", "capture-0001").with(verifier))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.photoStored").value(true))
            .andReturn();
        final MvcResult retry = mvc.perform(multipart("/v1/field/captures").file(metadata).file(photo)
                .header("Idempotency-Key", "capture-0001").with(verifier))
            .andExpect(status().isCreated())
            .andReturn();

        // THEN one change request exists, from the field, with its photo in Record Store
        final UUID changeRequestId = UUID.fromString(body(first).get("changeRequestId").asString());
        assertThat(body(retry).get("changeRequestId").asString()).isEqualTo(changeRequestId.toString());
        final ChangeRequestDTO changeRequest = changeRequests.find(changeRequestId).orElseThrow();
        assertThat(changeRequest.source()).isEqualTo("field");
        assertThat(changeRequest.kind()).isEqualTo("new_object");
        assertThat(changeRequest.photoObjectKey()).isNotNull();
        assertThat(photos.exists(changeRequest.photoObjectKey())).isTrue();
        assertThat(dsl.fetchCount(dsl.selectFrom("register.change_request")
            .where("photo_object_key = ?", changeRequest.photoObjectKey()))).isEqualTo(1);
    }

    @Test
    void theProposerCanNeverApproveTheirOwnChange() throws Exception {
        // GIVEN a change request proposed by editor-2 of demo-city
        final UUID target = objectIdOfHouse("Jacaranda Close", 7);
        final MvcResult created = mvc.perform(post("/v1/change-requests").header("Idempotency-Key", "editor2-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"kind": "retirement", "targetObjectId": "%s", "summary": "Building demolished"}""".formatted(target))
                .with(user("editor-2", "demo-city", Role.CUSTODIAN_EDITOR, Role.CUSTODIAN_APPROVER)))
            .andExpect(status().isCreated())
            .andReturn();
        final UUID id = UUID.fromString(body(created).get("id").asString());

        // WHEN the proposer tries to approve it THEN the four-eyes rule forbids it
        final CurrentActor proposer = new CurrentActor("editor-2", "demo-city",
            Set.of("custodian-editor", "custodian-approver"), false);
        assertThatThrownBy(() -> changeRequests.decide(id, true, proposer))
            .isInstanceOf(ProblemException.class)
            .hasMessageContaining("Four-eyes");

        // WHEN another approver of the same custodian approves it THEN it is approved and audited
        final CurrentActor approver = new CurrentActor("approver-1", "demo-city", Set.of("custodian-approver"), false);
        final ChangeRequestDTO decided = changeRequests.decide(id, true, approver);
        assertThat(decided.state()).isEqualTo("approved");
        assertThat(decided.decidedBy()).isEqualTo("approver-1");
        assertThat(dsl.fetchCount(dsl.selectFrom("register.audit_event")
            .where("action = 'change_request.approved' and entity_id = ?::uuid", id.toString()))).isEqualTo(1);
    }
}
