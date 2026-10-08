package org.ugaddress.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.ugaddress.register.shared.CurrentActor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /v1/search} and {@code /v1/reverse} against the synthetic fixtures in real PostGIS.
 */
class SearchApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private JsonMapper json;

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

    /** Longitude and latitude of a house's building. */
    private double[] locationOfHouse(final String street, final int number) {
        final Record r = dsl.fetchSingle("""
                select ST_X(o.location), ST_Y(o.location) from register.addressable_object o
                  join register.address a on a.object_id = o.id
                  join register.thoroughfare t on t.id = a.thoroughfare_id
                 where t.name = ? and a.house_number = ?""", street, Integer.toString(number));
        return new double[] {r.get(0, Double.class), r.get(1, Double.class)};
    }

    private JsonNode body(final MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> firstLines(final JsonNode page) {
        final List<String> lines = new ArrayList<>();
        page.get("items").forEach(item -> lines.add(item.at("/address/lines/0").asString()));
        return lines;
    }

    @Test
    void ranksAStreetsAddressesFirstInHouseNumberOrder() throws Exception {
        // GIVEN the twelve addresses on Amani Avenue
        // WHEN the street is searched
        final JsonNode page = body(mvc.perform(get("/v1/search").param("q", "Amani Avenue").param("limit", "12"))
            .andExpect(status().isOk()).andReturn());

        // THEN they come first, in house number order
        assertThat(firstLines(page)).containsExactly("1 Amani Avenue", "2 Amani Avenue", "3 Amani Avenue",
            "4 Amani Avenue", "5 Amani Avenue", "6 Amani Avenue", "7 Amani Avenue", "8 Amani Avenue",
            "9 Amani Avenue", "10 Amani Avenue", "11 Amani Avenue", "12 Amani Avenue");
    }

    @Test
    void ranksTheBestMatchFirst() throws Exception {
        // GIVEN a facility whose name contains words that also match other objects on its street
        // WHEN it is searched by name
        final JsonNode page = body(mvc.perform(get("/v1/search").param("q", "Mirembe Road Health Centre"))
            .andExpect(status().isOk()).andReturn());

        // THEN the facility is first
        assertThat(page.at("/items/0/name").asString()).isEqualTo("Mirembe Road Health Centre (demo)");
    }

    @Test
    void toleratesTyposAndAccents() throws Exception {
        // GIVEN street names typed with a missing letter, or with accents
        for (final String query : List.of("Jacarnda Close", "Jacaranda Clse", "Jàcaranda Clöse", "amani avnue")) {
            // WHEN they are searched
            final JsonNode page = body(mvc.perform(get("/v1/search").param("q", query))
                .andExpect(status().isOk()).andReturn());

            // THEN the intended street's addresses come first
            final String street = query.toLowerCase().startsWith("amani") ? "Amani Avenue" : "Jacaranda Close";
            assertThat(firstLines(page)).as(query).isNotEmpty().first().asString().endsWith(" " + street);
        }
    }

    @Test
    void findsANationalIdInEveryDisplayFormFirst() throws Exception {
        // GIVEN the national ID of 5 Kitenge Lane in its 4-3-4 display forms
        final String id = nationalIdOfHouse("Kitenge Lane", 5);
        final String grouped = id.substring(0, 4) + " " + id.substring(4, 7) + " " + id.substring(7);
        for (final String query : List.of(grouped, "DEMO " + grouped, grouped.replace(' ', '-'), id)) {
            // WHEN it is searched
            mvc.perform(get("/v1/search").param("q", query))
                // THEN that object is the first result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].nationalId").value(id))
                .andExpect(jsonPath("$.items[0].address.lines[0]").value("5 Kitenge Lane"));
        }
    }

    @Test
    void findsAnObjectByThePrefixOfItsNationalIdAndByAlias() throws Exception {
        // GIVEN the first seven digits of an ID, and an alias value
        final String id = nationalIdOfHouse("Jacaranda Close", 7);
        final String prefix = id.substring(0, 4) + " " + id.substring(4, 7);

        // WHEN they are searched THEN the object is among the results, and the alias finds its object first
        final JsonNode page = body(mvc.perform(get("/v1/search").param("q", prefix)).andExpect(status().isOk())
            .andReturn());
        assertThat(page.get("items").valueStream().map(i -> i.get("nationalId").asString())).contains(id);
        mvc.perform(get("/v1/search").param("q", "JAC-0007"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].nationalId").value(id));
    }

    @Test
    void pagesThroughResultsWithACursor() throws Exception {
        // GIVEN a query with twelve results
        final JsonNode first = body(mvc.perform(get("/v1/search").param("q", "Amani Avenue").param("limit", "5"))
            .andExpect(status().isOk()).andReturn());

        // WHEN the next page is requested with the cursor
        final String cursor = first.get("nextCursor").asString();
        final JsonNode second = body(mvc.perform(get("/v1/search").param("q", "Amani Avenue").param("limit", "5")
            .param("cursor", cursor)).andExpect(status().isOk()).andReturn());

        // THEN it continues exactly where the first page ended
        assertThat(firstLines(first)).containsExactly("1 Amani Avenue", "2 Amani Avenue", "3 Amani Avenue",
            "4 Amani Avenue", "5 Amani Avenue");
        assertThat(firstLines(second)).containsExactly("6 Amani Avenue", "7 Amani Avenue", "8 Amani Avenue",
            "9 Amani Avenue", "10 Amani Avenue");
    }

    @Test
    void searchHidesResidentialEntranceCoordinatesFromThePublic() throws Exception {
        // GIVEN a residential house
        final String id = nationalIdOfHouse("Amani Avenue", 1);

        // WHEN it is searched anonymously and by a partner
        // THEN only the partner sees its residential entrance's location
        mvc.perform(get("/v1/search").param("q", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].entrances[0].residential").value(true))
            .andExpect(jsonPath("$.items[0].entrances[0].location").doesNotExist());
        mvc.perform(get("/v1/search").param("q", id).with(partner()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].entrances[0].location.type").value("Point"));
    }

    @Test
    void rejectsQueriesThatAreTooShortAndMalformedCursors() throws Exception {
        // GIVEN / WHEN a one-character query and a forged cursor are sent THEN both are bad requests
        mvc.perform(get("/v1/search").param("q", "a"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("q: size must be between 2 and 200"));
        mvc.perform(get("/v1/search").param("q", "Amani").param("cursor", "bm90LWEtY3Vyc29y"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void publicReverseLookupReturnsTheStreetOnly() throws Exception {
        // GIVEN the position of 4 Amani Avenue
        final double[] location = locationOfHouse("Amani Avenue", 4);

        // WHEN it is looked up anonymously
        final JsonNode page = body(mvc.perform(get("/v1/reverse")
                .param("lat", Double.toString(location[1])).param("lon", Double.toString(location[0])))
            .andExpect(status().isOk()).andReturn());

        // THEN the nearest street comes back at street precision: no object, distance rounded to 10 m
        final JsonNode nearest = page.at("/items/0");
        assertThat(nearest.get("precision").asString()).isEqualTo("street");
        assertThat(nearest.at("/thoroughfare/name").asString()).isEqualTo("Amani Avenue");
        assertThat(nearest.get("postcode").asString()).isEqualTo("D102");
        assertThat(nearest.at("/adminUnits/0/name").asString()).isEqualTo("Amani Parish (demo)");
        assertThat(nearest.get("distanceMeters").asDouble() % 10).isZero();
        assertThat(nearest.path("object").isNull() || nearest.path("object").isMissingNode()).isTrue();
        assertThat(page.toString()).doesNotContain("nationalId").doesNotContain("houseNumber");
    }

    @Test
    void partnerReverseLookupReturnsTheNearestObjectWithItsEntrances() throws Exception {
        // GIVEN the position of 4 Amani Avenue
        final double[] location = locationOfHouse("Amani Avenue", 4);

        // WHEN a partner looks it up
        mvc.perform(get("/v1/reverse").with(partner())
                .param("lat", Double.toString(location[1])).param("lon", Double.toString(location[0])))
            // THEN the house itself is the nearest object, with its residential entrance's location
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].precision").value("object"))
            .andExpect(jsonPath("$.items[0].object.address.lines[0]").value("4 Amani Avenue"))
            .andExpect(jsonPath("$.items[0].distanceMeters").value(0.0))
            .andExpect(jsonPath("$.items[0].object.entrances[0].location.type").value("Point"));
    }

    @Test
    void reverseLookupFarFromAnyAddressFindsNothingAndValidatesItsInput() throws Exception {
        // GIVEN a point far outside the demo district
        // WHEN it is looked up THEN there are no matches
        mvc.perform(get("/v1/reverse").param("lat", "2.0").param("lon", "31.0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
        // AND an out-of-range latitude or radius is a bad request
        mvc.perform(get("/v1/reverse").param("lat", "91").param("lon", "32.59"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/v1/reverse").param("lat", "0.35").param("lon", "32.59").param("radius", "501"))
            .andExpect(status().isBadRequest());
    }
}
