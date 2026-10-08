package org.ugaddress.db.binding;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import org.jooq.Binding;
import org.jooq.BindingGetResultSetContext;
import org.jooq.BindingGetSQLInputContext;
import org.jooq.BindingGetStatementContext;
import org.jooq.BindingRegisterContext;
import org.jooq.BindingSQLContext;
import org.jooq.BindingSetSQLOutputContext;
import org.jooq.BindingSetStatementContext;
import org.jooq.Converter;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

/**
 * jOOQ binding between PostGIS {@code geometry} columns ({@link org.jooq.Geometry}) and JTS {@link Geometry}.
 *
 * <p>Values travel as hex-encoded EWKB (including the SRID), which PostgreSQL returns for geometry columns as text
 * and accepts via a {@code ::geometry} cast. Referenced by the generated jOOQ code (see codegen/JooqCodegen.java).
 */
public final class JtsGeometryBinding implements Binding<org.jooq.Geometry, Geometry> {

    private static final long serialVersionUID = 1L;

    private static final Converter<org.jooq.Geometry, Geometry> CONVERTER = Converter.ofNullable(
        org.jooq.Geometry.class, Geometry.class, JtsGeometryBinding::read, JtsGeometryBinding::write);

    /**
     * Creates the binding. Instantiated by jOOQ.
     */
    public JtsGeometryBinding() {
    }

    @Override
    public Converter<org.jooq.Geometry, Geometry> converter() {
        return CONVERTER;
    }

    @Override
    public void sql(final BindingSQLContext<Geometry> ctx) throws SQLException {
        if (ctx.render().paramType() == ParamType.INLINED) {
            final org.jooq.Geometry value = ctx.convert(converter()).value();
            ctx.render().visit(DSL.inline(value == null ? null : value.data())).sql("::geometry");
        } else {
            ctx.render().sql(ctx.variable()).sql("::geometry");
        }
    }

    @Override
    public void register(final BindingRegisterContext<Geometry> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.VARCHAR);
    }

    @Override
    public void set(final BindingSetStatementContext<Geometry> ctx) throws SQLException {
        final org.jooq.Geometry value = ctx.convert(converter()).value();
        ctx.statement().setString(ctx.index(), value == null ? null : value.data());
    }

    @Override
    public void set(final BindingSetSQLOutputContext<Geometry> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void get(final BindingGetResultSetContext<Geometry> ctx) throws SQLException {
        ctx.convert(converter()).value(org.jooq.Geometry.geometryOrNull(ctx.resultSet().getString(ctx.index())));
    }

    @Override
    public void get(final BindingGetStatementContext<Geometry> ctx) throws SQLException {
        ctx.convert(converter()).value(org.jooq.Geometry.geometryOrNull(ctx.statement().getString(ctx.index())));
    }

    @Override
    public void get(final BindingGetSQLInputContext<Geometry> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    private static @Nullable Geometry read(final org.jooq.@Nullable Geometry value) {
        if (value == null) {
            return null;
        }
        try {
            return new WKBReader().read(WKBReader.hexToBytes(value.data()));
        } catch (final ParseException e) {
            throw new IllegalStateException("Cannot parse geometry returned by the database", e);
        }
    }

    private static org.jooq.@Nullable Geometry write(final @Nullable Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        return org.jooq.Geometry.valueOf(WKBWriter.toHex(new WKBWriter(2, true).write(geometry)));
    }
}
