package fr.openent.pmb.services.impl;

import fr.openent.pmb.Pmb;
import fr.openent.pmb.services.SchoolService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jResult;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

public class DefaultSchoolService implements SchoolService {

    @Override
    public void listNeo(Handler<Either<String, JsonArray>> handler) {
        String query = "MATCH (s:Structure) WHERE s.UAI IS NOT NULL RETURN s.id as idneo, s.UAI as uai, s.name as nom;";
        Neo4j.getInstance().execute(query, new JsonObject(), Neo4jResult.validResultHandler(handler));
    }

    @Override
    public void list(Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * FROM " + Pmb.SCHOOL_TABLE + ";";
        Sql.getInstance().raw(query, SqlResult.validResultHandler(handler));
    }

    @Override
    public void create(JsonArray schools, Handler<Either<String, JsonArray>> handler) {
        String query = "INSERT INTO " + Pmb.SCHOOL_TABLE +
                " (idneo, uai, nom, principal, id_principal, pmb_host, pmb_endpoint, pmb_source_id, pmb_username, pmb_password, pmb_page_size) VALUES ";
        JsonArray params = new JsonArray();

        for (int i = 0; i < schools.size(); i++) {
            query += "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?), ";
            JsonObject school = schools.getJsonObject(i);
            params.add(school.getString("idneo", ""))
                .add(school.getString("uai", ""))
                .add(school.getString("nom", ""))
                .add(school.getBoolean("principal", true))
                .add(school.getInteger("id_principal", null))
                .add(school.getString("pmbHost", null))
                .add(school.getString("pmbEndpoint", null))
                .add(school.getString("pmbSourceId", null))
                .add(school.getString("pmbUsername", null))
                .add(school.getString("pmbPassword", null))
                .add(school.getInteger("pmbPageSize", null));
        }

        query = query.substring(0, query.length() - 2) + ";";
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    @Override
    public void updateConnection(String schoolId, JsonObject connection, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE " + Pmb.SCHOOL_TABLE +
                " SET pmb_host = ?, pmb_endpoint = ?, pmb_source_id = ?, pmb_username = ?, pmb_password = ?, pmb_page_size = ?" +
                " WHERE id = ?;";
        JsonArray params = new JsonArray()
                .add(connection.getString("pmbHost", null))
                .add(connection.getString("pmbEndpoint", null))
                .add(connection.getString("pmbSourceId", null))
                .add(connection.getString("pmbUsername", null))
                .add(connection.getString("pmbPassword", null))
                .add(connection.getInteger("pmbPageSize", null))
                .add(schoolId);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void delete(String schoolId, Handler<Either<String, JsonObject>> handler) {
        String query = "DELETE FROM " + Pmb.SCHOOL_TABLE + " WHERE id = ? OR id_principal = ?;";
        JsonArray params = new JsonArray().add(schoolId).add(schoolId);
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }

    @Override
    public void getCity(String uai, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT s.uai FROM " + Pmb.SCHOOL_TABLE + " AS m " +
                "INNER JOIN " + Pmb.SCHOOL_TABLE + " AS s ON m.id = s.id_principal " +
                "WHERE m.uai = ? " +
                "UNION SELECT m.uai FROM " + Pmb.SCHOOL_TABLE + " AS m " +
                "INNER JOIN " + Pmb.SCHOOL_TABLE + " AS s ON m.id = s.id_principal " +
                "WHERE s.uai = ? " +
                "UNION SELECT s2.uai FROM " + Pmb.SCHOOL_TABLE + " AS m " +
                "INNER JOIN " + Pmb.SCHOOL_TABLE + " AS s ON m.id = s.id_principal " +
                "INNER JOIN " + Pmb.SCHOOL_TABLE + " AS s2 ON m.id = s2.id_principal " +
                "WHERE s.uai = ?;";
        JsonArray params = new JsonArray().add(uai).add(uai).add(uai);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

}
