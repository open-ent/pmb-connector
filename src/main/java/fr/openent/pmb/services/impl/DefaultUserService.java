package fr.openent.pmb.services.impl;

import fr.openent.pmb.controllers.PmbController;
import fr.openent.pmb.core.constants.Field;
import fr.openent.pmb.services.UserService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jResult;


public class DefaultUserService implements UserService {
    private static final Logger log = LoggerFactory.getLogger(DefaultUserService.class);

    private Future<JsonArray> getUsersByStructure(JsonArray UAIs, String type){
        Promise<JsonArray> promise = Promise.promise();
        String query = "MATCH (s:Structure)<-[:DEPENDS]-(cpg:ProfileGroup)"+
                "OPTIONAL MATCH (cpg)-[:HAS_PROFILE]->(p:Profile)\n" +
                "OPTIONAL MATCH (cpg)<-[:IN]-(u:User)\n" +
                "WHERE s.UAI IN $uais\n" +
                "AND (p.name IN $type)\n" +
                "RETURN DISTINCT\n" +
                "u.externalId as externalId,\n" +
                "u.email as email,\n" +
                "u.address as address,\n" +
                "u.zipCode as zipCode,\n" +
                "u.country as country,\n" +
                "u.city as city;";
        JsonObject params = new JsonObject()
                .put("uais", UAIs)
                .put("type", type);

        Neo4j.getInstance().execute(query, params, Neo4jResult.validResultHandler(event -> {
            if (event.isLeft()) {
                log.error("[Pmb-Connector@DefaultUserService::getUsersByStructure] Error when trying to execute query" + event.left().getValue());
                promise.fail(event.left().getValue());
            } else {
                promise.complete(event.right().getValue());
            }
        }));
        return promise.future();
    }

    public Future<JsonArray> mergeUserInfos(JsonArray users, JsonArray UAIs, String type) {
        Promise<JsonArray> promise = Promise.promise();
        this.getUsersByStructure(UAIs, type)
                .onSuccess(usersInfos -> {
                    users.forEach(user -> {
                        JsonObject userObject = (JsonObject) user;
                        String externalId = userObject.getString(Field.EXTERNAL_ID);
                        usersInfos.stream()
                                .filter(infos -> externalId.equals(((JsonObject) infos).getString(Field.EXTERNAL_ID)))
                                .findFirst()
                                .ifPresent(infoObject -> {
                                    JsonObject info = (JsonObject) infoObject;
                                    userObject.put(Field.EMAIL, info.getString(Field.EMAIL));
                                    userObject.put(Field.ADDRESS, info.getString(Field.ADDRESS));
                                    userObject.put(Field.ZIP_CODE, info.getString(Field.ZIP_CODE));
                                    userObject.put(Field.COUNTRY, info.getString(Field.COUNTRY));
                                    userObject.put(Field.CITY, info.getString(Field.CITY));
                                });
                    });
                    promise.complete(users);
                })
                .onFailure( err -> {
                    log.error("[Pmb-Connector@DefaultUserService::mergeUserInfos] Error when trying to get users by structure" + err.getMessage());
                    promise.fail(err);
                });
        return promise.future();
    }
}