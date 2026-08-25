package fr.openent.pmb.server;

import fr.openent.pmb.bean.Credential;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;

import java.net.URI;
import java.net.URISyntaxException;

public class PMBServer {
    private Logger log = LoggerFactory.getLogger(PMBServer.class);

    private Credential credential;
    private String endpoint;
    private HttpClient httpClient;
    private String host;
    private int pageSize;
    private String sourceId;

    private PMBServer() {
    }

    public static PMBServer getInstance() {
        return PMBServerHolder.instance;
    }

    public void init(Vertx vertx, JsonObject config) {
        if (!config.containsKey("host") || !config.containsKey("endpoint") || !config.containsKey("source_id")
                || !config.containsKey("credentials") || config.getJsonObject("credentials", new JsonObject()).isEmpty()) {
            throw new RuntimeException("Unable to init PMB server instance. Please fill PMB configuration");
        }

        this.host = config.getString("host");
        this.endpoint = config.getString("endpoint");
        // Identifiant de la source de connecteur sortant "apijsonrpc" créée côté admin PMB
        // (Administration > Connecteurs > Sortants) : ws/connector_out.php l'exige en paramètre
        // ?source_id=, cf. admin/connecteurs/out/apijsonrpc/apijsonrpc.class.php côté PMB (PAS un
        // paramètre "database", qui n'existe dans aucune version de ce dispatcher).
        this.sourceId = config.getString("source_id");
        this.pageSize = config.getInteger("page_size", 200);
        JsonObject credentials = config.getJsonObject("credentials");
        this.credential = new Credential(credentials.getString("username"), credentials.getString("password"));
        initHttpClient(vertx);
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
                    Buffer body = Buffer.buffer();
                    response.bodyHandler(body::appendBuffer);
                    response.endHandler(aVoid -> handler.handle(Future.succeededFuture(new JsonObject(new String(body.getBytes())))));
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

    private static class PMBServerHolder {
        private static final PMBServer instance = new PMBServer();
    }
}
