package fr.openent.pmb;

import fr.openent.pmb.controllers.EmailSendController;
import fr.openent.pmb.controllers.PmbController;
import fr.openent.pmb.controllers.SchoolController;
import fr.wseduc.webutils.email.EmailSender;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.entcore.common.email.EmailFactory;
import org.entcore.common.http.BaseServer;

public class Pmb extends BaseServer {

    public static String DB_SCHEMA;
    public static String SCHOOL_TABLE;

    public static final String STRUCTURE_EXPORT_RIGHT = "pmb.structure.export";

    public static final String MANAGER_TYPE_PRET = "pret";
    public static final String MANAGER_TYPE_CDI = "cdi";


    public static final String USER_TYPE_STUDENT = "Student";
    public static final String USER_TYPE_TEACHER = "Teacher";
    public static final String USER_TYPE_PERSONNEL = "Personnel";

    public static Vertx pmbVertx;
    public static JsonObject pmbConfig;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
      final Promise<Void> promise = Promise.promise();
      super.start(promise);
      promise.future()
        .compose(e -> this.initPmb())
        .onComplete(startPromise);
    }

    public Future<Void> initPmb() {
        DB_SCHEMA = config.getString("db-schema");
        SCHOOL_TABLE = DB_SCHEMA + ".etablissement";

        pmbVertx = vertx;
        pmbConfig = config;

        JsonObject exportConfig = config.getJsonObject("export");
        // Chaque établissement a désormais son propre serveur PMB, configuré en base
        // (pmb.etablissement) et enregistré à la volée à chaque amass() — cf.
        // PmbController.amass / PMBServer.register. Il n'y a donc plus de PMBServer
        // global à initialiser ici.
        EmailFactory emailFactory = EmailFactory.getInstance();
        EmailSender emailSender = emailFactory.getSender();

        addController(new PmbController(exportConfig));
        addController(new SchoolController());
        addController(new EmailSendController(emailSender, config.getString("infraMail", null)));
        return Future.succeededFuture();
    }

}
