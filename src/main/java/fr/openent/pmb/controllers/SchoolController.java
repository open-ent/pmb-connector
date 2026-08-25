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
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;

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

    @Post("/schools")
    @ApiDoc("Create schools")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void create(HttpServerRequest request) {
        RequestUtils.bodyToJsonArray(request, schools -> {
            schoolService.create(schools, arrayResponseHandler(request));
        });
    }

    @Put("/schools/:schoolId/connection")
    @ApiDoc("Configure or update the PMB server connection (host/endpoint/source_id/credentials) of a specific school")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void updateConnection(HttpServerRequest request) {
        String schoolId = request.getParam("schoolId");
        RequestUtils.bodyToJson(request, connection -> {
            schoolService.updateConnection(schoolId, connection, defaultResponseHandler(request));
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

}