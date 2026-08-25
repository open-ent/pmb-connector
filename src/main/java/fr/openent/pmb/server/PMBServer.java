package fr.openent.pmb.server;

import fr.openent.pmb.bean.Credential;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Un établissement = un serveur PMB (chacun son catalogue CDI). Il n'y a donc plus une
 * seule instance globale mais un registre par UAI, alimenté à chaque amass() à partir de
 * la configuration stockée en base (pmb.etablissement) — cf. PmbController.amass et
 * AmassWorker.
 */
public class PMBServer {
    private static final Logger log = LoggerFactory.getLogger(PMBServer.class);
    private static final Map<String, PMBServer> INSTANCES = new ConcurrentHashMap<>();

    private Credential credential;
    private String endpoint;
    private HttpClient httpClient;
    private String host;
    private int pageSize;
    private String sourceId;

    private PMBServer() {
    }

    /**
     * @param uai UAI (déjà résolu vers son établissement principal le cas échéant)
     * @return l'instance enregistrée pour cet établissement, ou null si aucune connexion
     *         PMB n'a été configurée pour lui.
     */
    public static PMBServer get(String uai) {
        return INSTANCES.get(uai);
    }

    /**
     * Enregistre (ou remplace) l'instance PMB d'un établissement à partir de sa
     * configuration stockée en base. Retourne null si la configuration est incomplète
     * (établissement pas encore paramétré côté admin PMB) au lieu de lever une exception :
     * un établissement mal configuré ne doit jamais faire échouer l'amass des autres.
     */
    public static PMBServer register(Vertx vertx, String uai, JsonObject config) {
        if (config == null
                || isBlank(config.getString("host"))
                || isBlank(config.getString("endpoint"))
                || isBlank(config.getString("source_id"))
                || config.getJsonObject("credentials", new JsonObject()).isEmpty()) {
            log.warn("[PMB@PMBServer::register] Configuration PMB incomplète pour l'établissement " + uai
                    + ", amass ignoré pour cet établissement.");
            return null;
        }

        PMBServer server = new PMBServer();
        server.host = config.getString("host");
        server.endpoint = config.getString("endpoint");
        // Identifiant de la source de connecteur sortant "apijsonrpc" créée côté admin PMB
        // (Administration > Connecteurs > Sortants) : ws/connector_out.php l'exige en paramètre
        // ?source_id=, cf. admin/connecteurs/out/apijsonrpc/apijsonrpc.class.php côté PMB (PAS un
        // paramètre "database", qui n'existe dans aucune version de ce dispatcher).
        server.sourceId = config.getString("source_id");
        server.pageSize = config.getInteger("page_size", 200);
        JsonObject credentials = config.getJsonObject("credentials");
        server.credential = new Credential(credentials.getString("username"), credentials.getString("password"));
        server.initHttpClient(vertx);

        INSTANCES.put(uai, server);
        return server;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void initHttpClient(Vertx vertx) {
        try {
            final HttpClientOptions options = new HttpClientOptions();

            URI uri = new URI(host);
            options.setDefaultHost(host)
                    .setDefaultPort("https".equals(uri.getScheme()) ? 433 : 80)
                    .setSsl("https".equals(uri.getScheme()))
                    .setKeepAlive(true)
                    .setVerifyHost(false)
                    .setTrustAll(true);

            if (System.getProperty("httpclient.proxyHost") != null) {
                ProxyOptions proxyOptions = new ProxyOptions()
                        .setType(ProxyType.HTTP)
                        .setHost(System.getProperty("httpclient.proxyHost"))
                        .setPort(Integer.parseInt(System.getProperty("httpclient.proxyPort")));
                options.setProxyOptions(proxyOptions);
            }

            this.httpClient = vertx.createHttpClient(options);
        } catch (URISyntaxException | NumberFormatException e) {
            log.error("Failed to init http client", e);
        }
    }

    public int pageSize() {
        return this.pageSize;
    }

    private String uri() {
        String h = endpoint.startsWith("/") ? host : host + "";
        return String.format("%s%s?source_id=%s", h, endpoint, sourceId);
    }

    public void request(JsonObject content, Handler<AsyncResult<JsonObject>> handler) {
        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(this.uri())
                .setMethod(HttpMethod.POST)
                .addHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
                .addHeader(HttpHeaders.AUTHORIZATION.toString(), String.format("Basic %s", credential.basic()));

        httpClient.request(requestOptions)
                .flatMap(request -> request.send(content.encode()))
                .onSuccess(response -> {
                    if (response.statusCode() != 200) {
                        log.error("[PMB@PMBServer::request] " + response.statusCode(), response.statusMessage());
                        handler.handle(Future.failedFuture(response.statusMessage()));
                        return;
                    }
                    // bodyHandler() installe déjà son propre endHandler pour livrer le buffer
                    // complet une fois le flux terminé — enregistrer un endHandler séparément
                    // par-dessus l'écrasait avant qu'il ait pu copier quoi que ce soit dans le
                    // buffer, qui restait donc toujours vide ("No content to map due to
                    // end-of-input" quel que soit le contenu réellement reçu).
                    response.bodyHandler(body -> handler.handle(Future.succeededFuture(new JsonObject(body.toString()))));
                    response.exceptionHandler(throwable -> handler.handle(Future.failedFuture(throwable)));
                })
                .onFailure(throwable -> {
                    log.error("[PMB@PMBServer::request] " + throwable.getMessage(), throwable);
                    handler.handle(Future.failedFuture(throwable));
                });
    }

    public String host() {
        return this.host;
    }

}
