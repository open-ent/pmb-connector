package fr.openent.pmb.controllers;

import fr.openent.pmb.services.SchoolService;
import fr.openent.pmb.services.impl.DefaultSchoolService;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.AdminFilter;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

public class SchoolController extends ControllerHelper {
    private final SchoolService schoolService;

    public SchoolController() {
        super();
        this.schoolService = new DefaultSchoolService();
    }

    @Get("/schools/neo")
    @ApiDoc("List all the schools in neo4j")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void listNeo(HttpServerRequest request) {
        schoolService.listNeo(arrayResponseHandler(request));
    }

    @Get("/schools")
    @ApiDoc("List all the schools")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void list(HttpServerRequest request) {
        schoolService.list(arrayResponseHandler(request));
    }

    /**
     * Établissement(s) de l'utilisateur courant. Contrairement à GET /schools (réservé au
     * super-administrateur, vue d'ensemble), un administrateur local n'a le droit de voir que
     * SES PROPRES établissements — c'est ce que cette route restreint, en filtrant sur
     * user.getStructures(). Le super-administrateur voit ici la même chose que /schools.
     */
    @Get("/schools/mine")
    @ApiDoc("List the schools of the current user's own structures (local admin) or all schools (super admin)")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(AdminFilter.class)
    public void listMine(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            schoolService.list(handler -> {
                if (handler.isLeft()) {
                    arrayResponseHandler(request).handle(handler);
                    return;
                }
                JsonArray schools = handler.right().getValue();
                JsonArray filtered = isSuperAdmin(user) ? schools : filterOwnStructures(schools, user);
                arrayResponseHandler(request).handle(new Either.Right<>(filtered));
            });
        });
    }

    @Post("/schools")
    @ApiDoc("Create schools")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void create(HttpServerRequest request) {
        RequestUtils.bodyToJsonArray(request, schools -> {
            schoolService.create(schools, arrayResponseHandler(request));
        });
    }

    /**
     * Réservé au super-administrateur (SuperAdminFilter) : create() ci-dessus crée des
     * établissements arbitraires (idneo quelconque, y compris pour orchestrer une cité
     * scolaire), ce n'est pas une opération qu'un administrateur local doit pouvoir faire pour
     * n'importe quel idneo. Configurer la CONNEXION d'un établissement déjà créé, en revanche,
     * est ouvert à l'administrateur local — cf. updateConnection ci-dessous.
     */
    @Put("/schools/:schoolId/connection")
    @ApiDoc("Configure or update the PMB server connection (host/endpoint/opac_url/source_id/credentials) of a specific school")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(AdminFilter.class)
    public void updateConnection(HttpServerRequest request) {
        String schoolId = request.getParam("schoolId");
        UserUtils.getUserInfos(eb, request, user -> {
            if (user == null) {
                unauthorized(request);
                return;
            }
            if (isSuperAdmin(user)) {
                RequestUtils.bodyToJson(request, connection ->
                        schoolService.updateConnection(schoolId, connection, defaultResponseHandler(request)));
                return;
            }
            // Administrateur local : vérifier qu'il agit bien sur SON établissement avant
            // d'écrire quoi que ce soit — sans ce contrôle, AdminFilter seul autoriserait un
            // administrateur local à modifier la connexion PMB de N'IMPORTE QUEL établissement.
            schoolService.list(handler -> {
                if (handler.isLeft()) {
                    forbidden(request);
                    return;
                }
                boolean owns = handler.right().getValue().stream()
                        .map(JsonObject.class::cast)
                        .filter(school -> String.valueOf(school.getValue("id")).equals(schoolId))
                        .anyMatch(school -> isOwnStructure(school, user));
                if (!owns) {
                    forbidden(request);
                    return;
                }
                RequestUtils.bodyToJson(request, connection ->
                        schoolService.updateConnection(schoolId, connection, defaultResponseHandler(request)));
            });
        });
    }

    @Delete("/schools/:schoolId")
    @ApiDoc("Delete a scpecific school")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void delete(HttpServerRequest request) {
        String schoolId = request.getParam("schoolId");
        schoolService.delete(schoolId, defaultResponseHandler(request));
    }

    private boolean isSuperAdmin(UserInfos user) {
        return user.getFunctions() != null && user.getFunctions().containsKey(DefaultFunctions.SUPER_ADMIN);
    }

    private boolean isOwnStructure(JsonObject school, UserInfos user) {
        String idneo = school.getString("idneo");
        List<String> structures = user.getStructures();
        return idneo != null && structures != null && structures.contains(idneo);
    }

    private JsonArray filterOwnStructures(JsonArray schools, UserInfos user) {
        return new JsonArray(schools.stream()
                .map(JsonObject.class::cast)
                .filter(school -> isOwnStructure(school, user))
                .collect(Collectors.toList()));
    }

}
