package fr.openent.pmb.services.impl;

import fr.openent.pmb.Pmb;
import fr.openent.pmb.core.constants.Field;
import fr.openent.pmb.helper.FutureHelper;
import fr.openent.pmb.services.ExportService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jResult;

import java.util.Arrays;
import java.util.List;


public class DefaultExportService implements ExportService {

    private final JsonObject exportConfig;

    public DefaultExportService(JsonObject exportConfig) {
        this.exportConfig = exportConfig;
    }

    @Override
    public void getUser(JsonArray UAIs, String type, Handler<Either<String, JsonArray>> handler) {
        String query = "MATCH (s:Structure)<-[:DEPENDS]-(g:ManualGroup {name:{group_name}})<-[:IN]-(u:User) " +
                "WHERE s.UAI IN {uais} " +
                "RETURN DISTINCT u.externalId as externalId, u.firstName as firstName, u.lastName as lastName, u.login as login, u.id as id, " +
                "CASE WHEN u.emailInternal IS NULL THEN u.email ELSE u.emailInternal END as emailInterne;";

        JsonObject params = new JsonObject().put("uais", UAIs);

        // Type of manager
        if (type == null || type.isEmpty()) {
            params.put("group_name", exportConfig.getString("group_manager"));
        }
        else {
            switch (type) {
                case Pmb.MANAGER_TYPE_PRET :
                    params.put("group_name", exportConfig.getString("group_manager_lend_manual"));
                    break;
                case Pmb.MANAGER_TYPE_CDI :
                    params.put("group_name", exportConfig.getString("group_manager"));
                    break;
                default:
                    handler.handle(new Either.Left<>("[PMB@getUser] The type parameter '" + type + "' is unknown."));
            }
        }

        Neo4j.getInstance().execute(query, params, Neo4jResult.validResultHandler(handler));
    }

    @Override
    public Future<JsonArray> getUsers(JsonArray UAIs, String type) {
        Promise<JsonArray> promise = Promise.promise();

        this.getUser(UAIs, type, FutureHelper.handlerJsonArray(promise));

        return promise.future();
    }

    @Override
    public Future<JsonArray> getUserLendGroupUAIs(List<String> userIds) {
        Promise<JsonArray> promise = Promise.promise();

        String query = "MATCH (s:Structure)<-[:DEPENDS]-(g:ManualGroup {name:{group_name}})<-[:IN]-(u:User) " +
                "WHERE u.id IN {user_ids} " +
                "RETURN u.id as id, collect(s.UAI) as uais;";


        JsonObject params = new JsonObject().put(Field.GROUP_NAME, exportConfig.getString(Field.GROUP_MANAGER_LEND_MANUAL))
                .put(Field.USER_IDS, new JsonArray(userIds));


        Neo4j.getInstance().execute(query, params, Neo4jResult.validResultHandler(FutureHelper.handlerJsonArray(promise)));


        return promise.future();
    }

    @Override
    public void retrieveDeployedStructures(Handler<Either<String, JsonArray>> handler) {
        // Bug historique : "s.export" (singulier) au lieu de "s.exports" (pluriel, la
        // propriété réellement écrite ailleurs dans entcore, cf. org.entcore.directory.
        // DefaultSchoolService) — la condition n'était donc jamais vraie, aucun
        // établissement n'était jamais "trouvé déployé", l'amass PMB tournait toujours à
        // vide silencieusement (masqué en plus par le root WARN du logger, cf. commit du
        // fix logback-ent.xml).
        String query = "MATCH(s:Structure) WHERE HAS(s.exports) AND 'PMB' IN s.exports RETURN DISTINCT s.UAI as uai, s.id as id";
        Neo4j.getInstance().execute(query, new JsonObject(), Neo4jResult.validResultHandler(handler));
    }
}
