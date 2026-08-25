package fr.openent.pmb.bean.request;

import fr.openent.pmb.server.PMBMethod;
import fr.openent.pmb.server.PMBServer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class PMBSimpleSearch {
    private String uai;
    private String term;

    public PMBSimpleSearch(String term, String uai) {
        this.uai = uai;
        this.term = term;
    }

    private JsonObject generate() {
        JsonObject params = new JsonObject()
                .put("searchType", 1)
                .put("searchTerm", term)
                .put("pmbUserId", -1)
                .put("OPACUserId", -1);

        return new JsonObject()
                .put("method", PMBMethod.pmbesSearch_simpleSearch.name())
                .put("params", params)
                .put("id", 1);
    }

    public void execute(Handler<AsyncResult<JsonObject>> handler) {
        PMBServer.getInstance().request(generate(), handler);
    }
}
