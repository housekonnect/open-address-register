package org.ugaddress.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Database guarantees that hold regardless of application code: row-level security, history twins, the audit hash
 * chain and the uniqueness constraints. Uses plain JDBC as the runtime role {@code register_app}.
 */
class DatabaseRulesIT extends AbstractIntegrationTest {

    private static Connection app() throws SQLException {
        return DriverManager.getConnection(TestInfrastructure.jdbcUrl(), "register_app", TestInfrastructure.APP_PASSWORD);
    }

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(TestInfrastructure.jdbcUrl(), "register_owner",
            TestInfrastructure.OWNER_PASSWORD);
    }

    private static String jurisdictionOf(final Connection connection, final String custodianCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                with recursive units as (
                    select j.admin_unit_id as id from register.jurisdiction j
                      join register.custodian c on c.id = j.custodian_id where c.code = ?
                    union select a.id from register.admin_unit a join units u on a.parent_id = u.id)
                select string_agg(id::text, ',') from units""")) {
            statement.setString(1, custodianCode);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static void enter(final Connection connection, final String units) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config('app.jurisdictions', ?, true), set_config('app.subject', 'test-subject', true)")) {
            statement.setString(1, units);
            statement.execute();
        }
    }

    private static int update(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private static long count(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void fixturesContainFiftyDemonstrationBuildings() throws SQLException {
        // GIVEN the synthetic fixtures
        try (Connection connection = app()) {
            // WHEN buildings and facilities are counted
            final long buildings = count(connection,
                "select count(*) from register.addressable_object where kind in ('building', 'facility')");
            final long nonDemo = count(connection,
                "select count(*) from register.addressable_object where national_id_status <> 'demonstration'");

            // THEN there are 50, all with demonstration IDs
            assertThat(buildings).isEqualTo(50);
            assertThat(nonDemo).isZero();
        }
    }

    @Test
    void rowLevelSecurityLimitsWritesToTheJurisdiction() throws SQLException {
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            // GIVEN no jurisdiction
            // WHEN all streets are updated THEN nothing changes
            assertThat(update(connection, "update register.thoroughfare set name = name")).isZero();

            // GIVEN the jurisdiction of demo-city (Amani parish)
            enter(connection, jurisdictionOf(connection, "demo-city"));
            // WHEN all streets are updated THEN only its two streets change
            assertThat(update(connection, "update register.thoroughfare set name = name")).isEqualTo(2);
            connection.rollback();

            // GIVEN the jurisdiction of demo-city
            enter(connection, jurisdictionOf(connection, "demo-city"));
            // WHEN a street is inserted into Mirembe parish (demo-district) THEN row-level security rejects it
            assertThatThrownBy(() -> update(connection, """
                    insert into register.thoroughfare (admin_unit_id, name, centreline)
                    select id, 'Intruder Road', ST_SetSRID(ST_MakeLine(ST_MakePoint(32.575, 0.345),
                                                                       ST_MakePoint(32.576, 0.345)), 4326)
                      from register.admin_unit where code = 'DEMO-P1'"""))
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("42501");
            connection.rollback();
        }
    }

    @Test
    void historyTwinRecordsEveryChangeWithTheActor() throws SQLException {
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            // GIVEN demo-city's jurisdiction and a street
            enter(connection, jurisdictionOf(connection, "demo-city"));
            final long before = count(connection,
                "select count(*) from register.thoroughfare_history where name = 'Amani Avenue'");

            // WHEN the street is updated and committed
            update(connection, "update register.thoroughfare set valid_from = valid_from where name = 'Amani Avenue'");
            connection.commit();

            // THEN one history row is added, with operation, actor and a higher version
            assertThat(count(connection,
                "select count(*) from register.thoroughfare_history where name = 'Amani Avenue'")).isEqualTo(before + 1);
            assertThat(count(connection, """
                    select count(*) from register.thoroughfare_history
                     where name = 'Amani Avenue' and history_operation = 'update' and history_actor = 'test-subject'
                       and version > 1""")).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void auditChainStaysIntactUnderConcurrentAppends() throws Exception {
        // GIVEN eight concurrent writers
        final ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            final List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    for (int j = 0; j < 10; j++) {
                        try (Connection connection = app(); Statement statement = connection.createStatement()) {
                            // WHEN each appends audit events
                            statement.execute("select register.append_audit_event('test', null, 'test.concurrent', "
                                + "'test', null, '{}'::jsonb)");
                        }
                    }
                    return null;
                }));
            }
            for (final Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }
        try (Connection connection = app(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select register.verify_audit_chain()")) {
            // THEN the chain verifies
            rs.next();
            assertThat(rs.getObject(1)).isNull();
        }
    }

    @Test
    void auditEventsCannotBeChangedEvenByTheOwner() throws SQLException {
        try (Connection app = app(); Connection owner = owner()) {
            // GIVEN an audit log with events
            // WHEN the application role tries to delete THEN it lacks the privilege
            assertThatThrownBy(() -> update(app, "delete from register.audit_event"))
                .isInstanceOf(SQLException.class);
            // WHEN the owner tries to rewrite history THEN the trigger rejects it
            assertThatThrownBy(() -> update(owner, "update register.audit_event set actor = 'forged'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("append-only");
        }
    }

    @Test
    void onlyOneActiveAddressPerStreetHouseNumberAndUnit() throws SQLException {
        try (Connection owner = owner()) {
            owner.setAutoCommit(false);
            // GIVEN an existing active address
            // WHEN a second active address with the same street and number is inserted
            // THEN the exclusion constraint rejects it
            assertThatThrownBy(() -> update(owner, """
                    insert into register.address (object_id, thoroughfare_id, house_number, admin_unit_id)
                    select a.object_id, a.thoroughfare_id, a.house_number, a.admin_unit_id
                      from register.address a limit 1"""))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("address_one_active");
            owner.rollback();
        }
    }

    @Test
    void aliasIsUniquePerSystemAndValue() throws SQLException {
        try (Connection owner = owner()) {
            owner.setAutoCommit(false);
            // GIVEN an existing alias WHEN it is inserted again THEN the unique constraint rejects it
            assertThatThrownBy(() -> update(owner, """
                    insert into register.alias (object_id, system, value, admin_unit_id)
                    select object_id, system, value, admin_unit_id from register.alias limit 1"""))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("alias_system_value_unique");
            owner.rollback();
        }
    }

    @Test
    void fourEyesRuleIsAlsoEnforcedByTheDatabase() throws SQLException {
        try (Connection owner = owner()) {
            owner.setAutoCommit(false);
            // GIVEN a change request proposed by subject-a
            final UUID id;
            try (Statement statement = owner.createStatement(); ResultSet rs = statement.executeQuery("""
                    insert into register.change_request (kind, source, summary, thoroughfare_id, admin_unit_id,
                                                         custodian_id, proposed_by)
                    select 'correction', 'console', 'Rename street', t.id, t.admin_unit_id, c.id, 'subject-a'
                      from register.thoroughfare t, register.custodian c
                     where t.name = 'Amani Avenue' and c.code = 'demo-city'
                    returning id""")) {
                rs.next();
                id = rs.getObject(1, UUID.class);
            }
            // WHEN the proposer approves it directly in SQL THEN the check constraint rejects it
            assertThatThrownBy(() -> update(owner, "update register.change_request set state = 'approved', "
                + "decided_by = 'subject-a', decided_at = now() where id = '" + id + "'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("change_request_four_eyes");
            owner.rollback();
        }
    }
}
