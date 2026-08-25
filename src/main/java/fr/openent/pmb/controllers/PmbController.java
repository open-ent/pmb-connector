package fr.openent.pmb.controllers;

import fr.openent.pmb.Pmb;
import fr.openent.pmb.core.constants.Field;
import fr.openent.pmb.helper.HttpClientHelper;
import fr.openent.pmb.services.ExportService;
import fr.openent.pmb.services.SchoolService;
import fr.openent.pmb.services.UserService;
import fr.openent.pmb.services.impl.DefaultExportService;
import fr.openent.pmb.services.impl.DefaultSchoolService;
import fr.openent.pmb.services.impl.DefaultUserService;
import fr.openent.pmb.worker.AmassWorker;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fr.openent.pmb.Pmb.pmbConfig;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

public class PmbController extends ControllerHelper {
    private static final Logger log = LoggerFactory.getLogger(PmbController.class);
    private final ExportService exportService;
    private final SchoolService schoolService;
    private final UserService userService;

    public PmbController(JsonObject exportConfig) {
        super();
        this.exportService = new DefaultExportService(exportConfig);
        this.schoolService = new DefaultSchoolService();
        this.userService = new DefaultUserService();
    }

    @Get("")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void render(HttpServerRequest request) {
        renderView(request);
    }

    @Get("/gestionnaire/list")
    @SecuredAction(Pmb.STRUCTURE_EXPORT_RIGHT)
    public void listGestionnaire(HttpServerRequest request) {
        String uai = request.getParam("uai");
        String type = request.getParam("type");
        if(uai == null){
            log.error("No uai sent in request pmb/gestionnaire/list");
            badRequest(request);
        }else{
            JsonArray UAIs = new JsonArray().add(uai);
            if(type != null && type.equals(Pmb.MANAGER_TYPE_PRET)) {
                JsonArray users = new JsonArray();
                exportService.getUsers(UAIs, type)
                        .compose(usersAsync -> {
                            users.addAll(usersAsync);
                            List<String> userIds = usersAsync.stream()
                                    .filter(JsonObject.class::isInstance)
                                    .map(JsonObject.class::cast)
                                    .map(user -> user.getString(Field.ID, ""))
                                    .collect(Collectors.toList());
                            userIds.remove("");
                            return exportService.getUserLendGroupUAIs(userIds);
                        })
                        .compose(usersWithUAIs -> this.addUaisToUsers(users, usersWithUAIs))
                        .onSuccess(formattedUsers -> Renders.renderJson(request, new JsonArray(formattedUsers)))
                        .onFailure(error -> {
                            String errorMessage = String.format("[Pmb-connector@%s::listGestionnaire]: an error has " +
                                            "occurred while finding users info: %s", this.getClass().getSimpleName(), error.getMessage());
                            log.error(errorMessage);
                            renderError(request, new JsonObject().put(Field.ERROR, error.getMessage()));
                        });
            }else{
                schoolService.getCity(uai, handler -> {
                    if (handler.isLeft()) {
                        log.error("Unable to retrieve structures from this city", handler.left().getValue());
                        badRequest(request);
                    } else {
                        JsonArray structures = handler.right().getValue();
                        buildUAIList(structures, UAIs, uai);
                        exportService.getUser(UAIs, type, arrayResponseHandler(request));
                    }
                });
            }
        }
    }

    private Future<List<JsonObject>> addUaisToUsers(JsonArray users, JsonArray usersWithUAIs) {
        Map<String, JsonArray> uaisMapByUserId = usersWithUAIs.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .collect(Collectors.toMap(e -> e.getString(Field.ID), entries -> entries.getJsonArray(Field.UAIS)));
        return Future.succeededFuture(users.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(user -> user.put(Field.UAIS, uaisMapByUserId.getOrDefault(user.getString(Field.ID), new JsonArray())))
                .collect(Collectors.toList()));
    }

    @Get("/user/structures/list")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void listUserInStructuresByUAI(final HttpServerRequest request) {
        String uai = request.getParam("uai");
        String type = request.getParam("type");
        if(uai == null || type == null){
            log.error("[Pmb-connector@PmbController::listUserInStructuresByUAI] No uai/type sent in request.");
            badRequest(request);
        }else{
            JsonArray UAIs = new JsonArray().add(uai);
            if (type.equals(Pmb.USER_TYPE_STUDENT) || type.equals(Pmb.USER_TYPE_TEACHER) || type.equals(Pmb.USER_TYPE_PERSONNEL)) {
                directoryUsersExport(request, UAIs, type);
            } else {
                log.error("[Pmb-connector@PmbController::listUserInStructuresByUAI] wrong type sent in request.");
                badRequest(request);
            }
        }
    }

    private void directoryUsersExport(HttpServerRequest request, JsonArray UAIs, String type) {
        StringBuilder url = new StringBuilder().append(pmbConfig.getString("host"))
                .append("/directory/user/structures/list?full=true&");
        for (Object UAI : UAIs) {
            url.append("uai=").append((String) UAI).append("&");
        }
        url.append("type=").append(type);
        try {
            HttpClientHelper.webServicePmbGet(url.toString(), request, handler -> {
                if (handler.isLeft()) {
                    log.error("[Pmb-connector@PmbController::directoryUsersExport] Unable to retrieve users from this structures" + handler.left().getValue());
                    badRequest(request);
                } else {
                    JsonArray users = handler.right().getValue();
                    userService.mergeUserInfos(users, UAIs, type)
                            .onSuccess(usersMerged -> {
                                Renders.renderJson(request, usersMerged);
                            })
                            .onFailure(err -> {
                                log.error("[Pmb-connector@PmbController::directoryUsersExport] Unable to merge users infos" + err.getMessage());
                                badRequest(request);
                            });
                }
            });
        } catch (UnsupportedEncodingException e) {
            log.error("[Pmb-connector@PmbController::directoryUsersExport] Failed to get users from directory module");
            e.printStackTrace();
        }
    }

    @BusAddress("fr.openent.pmb.controllers.PmbController|amass")
    public void amass(Message<JsonObject> message) {
        log.info("Starting PMB amass");
        exportService.retrieveDeployedStructures(handler -> {
            if (handler.isLeft()) {
                log.error("Unable to retrieve deployed structures", handler.left().getValue());
                return;
            }
            JsonArray structures = handler.right().getValue();
            schoolService.list(event -> {
                if (event.isLeft()) {
                    log.error("Unable to retrieve structures list in pmb SQL DataBase", event.left().getValue());
                    return;
                }
                JsonArray citeStructures = event.right().getValue();
                JsonObject map = new JsonObject();
                JsonObject connections = new JsonObject();
                structures.forEach(structure -> {
                    JsonObject s = (JsonObject) structure;
                    String uaiToReturn = principalOrDefaultUAI(citeStructures, s.getString("uai"));
                    String idStructure = s.getString("id");
                    if(!uaiToReturn.equals(s.getString("uai"))){
                        idStructure =  ((JsonObject) citeStructures.stream()
                                .filter(obj -> ((JsonObject) obj).getString("uai").equals(uaiToReturn)).findFirst().get())
                                .getString("idneo");
                    }
                    map.put(uaiToReturn, idStructure);
                    // Connexion PMB propre à cet établissement (chaque établissement a son
                    // propre catalogue) : portée par la ligne pmb.etablissement de l'UAI déjà
                    // résolu ci-dessus (le "principal" d'une cité scolaire, le cas échéant).
                    if (!connections.containsKey(uaiToReturn)) {
                        citeStructures.stream()
                                .filter(obj -> ((JsonObject) obj).getString("uai").equals(uaiToReturn))
                                .findFirst()
                                .map(JsonObject.class::cast)
                                .ifPresent(row -> connections.put(uaiToReturn, buildConnectionConfig(row)));
                    }
                });

                JsonObject workerConfig = new JsonObject()
                        .put("structures", map)
                        .put("connections", connections);
                DeploymentOptions options = new DeploymentOptions()
                        .setConfig(workerConfig)
                        .setWorker(true);
                vertx.deployVerticle(AmassWorker::new, options);

                message.reply(new JsonObject().put("status", "accepted"));
            });
        });
    }

    @BusAddress("fr.openent.pmb.controllers.PmbController|getPrincipalUAIs")
    public void getPrincipalUAIs(Message<JsonObject> message) {
        JsonObject body = message.body();
        JsonArray uais = body.getJsonArray("uais");
        schoolService.list(event -> {
            if (event.isLeft()) {
                log.error("Unable to retrieve structures list in pmb SQL DataBase", event.left().getValue());
                JsonObject json = (new JsonObject())
                        .put("status", "error")
                        .put("message", "invalid.action");
                message.reply(json);
            } else {
                JsonArray deployedStructures = event.right().getValue();
                JsonArray returnBody = new JsonArray();
                for(Object uai : uais){
                    String UAI = (String) uai;
                    returnBody.add(principalOrDefaultUAI(deployedStructures, UAI));
                }
                JsonObject response = new JsonObject()
                        .put("status", "ok")
                        .put("result", returnBody);
                message.reply(response);
            }
        });
    }

    /**
     * Construit la config de connexion PMB d'un établissement (host/endpoint/source_id/
     * credentials/page_size) à partir de sa ligne pmb.etablissement. Retourne un objet
     * incomplet (host/etc. absents) si l'établissement n'a pas encore été paramétré côté
     * admin PMB — PMBServer.register() se charge alors de l'ignorer proprement.
     */
    private JsonObject buildConnectionConfig(JsonObject school) {
        JsonObject config = new JsonObject();
        if (school.getString("pmb_host") != null) config.put("host", school.getString("pmb_host"));
        if (school.getString("pmb_endpoint") != null) config.put("endpoint", school.getString("pmb_endpoint"));
        if (school.getString("pmb_source_id") != null) config.put("source_id", school.getString("pmb_source_id"));
        config.put("page_size", school.getInteger("pmb_page_size", pmbConfig.getJsonObject("PMB", new JsonObject()).getInteger("page_size", 200)));
        if (school.getString("pmb_username") != null && school.getString("pmb_password") != null) {
            config.put("credentials", new JsonObject()
                    .put("username", school.getString("pmb_username"))
                    .put("password", school.getString("pmb_password")));
        }
        return config;
    }

    private String principalOrDefaultUAI(JsonArray deployedStructures, String UAI) {
        Stream<String> listUAISQL = deployedStructures.stream().map(obj -> ((JsonObject) obj).getString("uai"));
        if(listUAISQL.anyMatch(uaiSQL -> uaiSQL.equals(UAI))){
            JsonObject sqlInfos = (JsonObject) deployedStructures.stream()
                    .filter(obj -> ((JsonObject) obj).getString("uai").equals(UAI)).findFirst().get();
            if(sqlInfos.getBoolean("principal")){
                return UAI;
            }else{
                Integer idPrincipal = sqlInfos.getInteger("id_principal");
                JsonObject principalInfos = (JsonObject) deployedStructures.stream()
                        .filter(obj -> ((JsonObject) obj).getInteger("id").equals(idPrincipal)).findFirst().get();
                return principalInfos.getString("uai");
            }
        }else{
            return UAI;
        }
    }

    private void buildUAIList(JsonArray structures, JsonArray UAIs, String uaiParams) {
        for (int i = 0; i < structures.size(); i++) {
            JsonObject structure = structures.getJsonObject(i);
            String uaiToAdd = structure.getString("uai", "");
            if(!uaiParams.equals(uaiToAdd)){
                UAIs.add(uaiToAdd);
            }
        }
    }
}
