package fr.openent.pmb.bean.request;

import fr.openent.pmb.server.PMBMethod;
import fr.openent.pmb.server.PMBServer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PMBFetchSearchRecords {
    private String id;
    private int recordsCount;
    private int page;
    private String uai;

    public PMBFetchSearchRecords() {
        this.page = 0;
    }

    public PMBFetchSearchRecords setUAI(String uai) {
        this.uai = uai;
        return this;
    }

    public PMBFetchSearchRecords setSearchId(String id) {
        this.id = id;
        return this;
    }

    public PMBFetchSearchRecords setRecorsCount(int count) {
        this.recordsCount = count;
        return this;
    }

    public String uai() {
        return this.uai;
    }

    public String searchId() {
        return this.id;
    }

    public int recordsCount() {
        return this.recordsCount;
    }

    public int pagesCount() {
        int pageDiff = this.recordsCount % PMBServer.getInstance().pageSize();
        if (pageDiff == 0) return this.recordsCount / PMBServer.getInstance().pageSize();
        else return Math.round(this.recordsCount / PMBServer.getInstance().pageSize()) + 1;
    }

    private JsonObject generate() {
        JsonObject params = new JsonObject()
                .put("searchId", id)
                .put("firstRecord", page * PMBServer.getInstance().pageSize())
                .put("recordCount", PMBServer.getInstance().pageSize())
                .put("recordFormat", "json_unimarc")
                .put("recordCharset", "utf-8")
                .put("includeLinks", 1)
                .put("includeItems", 1);

        return new JsonObject()
                .put("method", PMBMethod.pmbesSearch_fetchSearchRecords.name())
                .put("params", params)
                .put("id", 1);
    }

    private void execute(Handler<AsyncResult<JsonObject>> handler) {
        PMBServer.getInstance().request(generate(), handler);
    }

    public void next(Handler<AsyncResult<JsonObject>> handler) {
        if ((this.page * PMBServer.getInstance().pageSize()) > this.recordsCount) {
            handler.handle(Future.succeededFuture(emptyResponse()));
            return;
        }

        this.execute(handler);
        this.page++;
    }

    private JsonObject emptyResponse() {
        return new JsonObject()
                .put("id", 1)
                .put("result", new JsonArray());
    }
}
