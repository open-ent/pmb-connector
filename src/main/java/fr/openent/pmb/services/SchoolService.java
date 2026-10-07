package fr.openent.pmb.services;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

import java.util.List;

public interface SchoolService {
    /**
     * List all schools in neo4j
     * @param handler function handler returning JsonArray data
     */
    void listNeo(Handler<Either<String, JsonArray>> handler);

    /**
     * List all schools
     * @param handler function handler returning JsonArray data
     */
    void list(Handler<Either<String, JsonArray>> handler);

    /**
     * Create a school
     * @param schools JsonArray data
     * @param handler function handler returning JsonArray data
     */
    void create(JsonArray schools, Handler<Either<String, JsonArray>> handler);

    /**
     * Update the PMB connection of a specific school (host/endpoint/source_id/credentials/
     * page_size) without touching its idneo/uai/nom/principal fields.
     * @param schoolId school identifier
     * @param connection JsonObject data (pmbHost, pmbEndpoint, pmbSourceId, pmbUsername, pmbPassword, pmbPageSize)
     * @param handler function handler returning JsonObject data
     */
    void updateConnection(String schoolId, JsonObject connection, Handler<Either<String, JsonObject>> handler);

    /**
     * Delete a scpecific school
     * @param schoolId school identifier
     * @param handler function handler returning JsonObject data
     */
    void delete(String schoolId, Handler<Either<String, JsonObject>> handler);

    /**
     * Get UAIs from schools in a city
     * @param uai school's uai
     * @param handler function handler returning JsonArray data
     */
    void getCity(String uai, Handler<Either<String, JsonArray>> handler);

}
