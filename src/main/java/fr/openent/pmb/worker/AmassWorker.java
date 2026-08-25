package fr.openent.pmb.worker;

import fr.openent.pmb.bean.BibliographicRecord;
import fr.openent.pmb.bean.Report;
import fr.openent.pmb.bean.request.PMBFetchSearchRecords;
import fr.openent.pmb.bean.request.PMBSimpleSearch;
import fr.openent.pmb.server.PMBServer;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public class AmassWorker extends AbstractVerticle {
    Logger log = LoggerFactory.getLogger(AmassWorker.class);
    Report report = new Report();

    @Override
    public void start() throws Exception {
        log.info(String.format("[Worker@%s] Starting PMB amass worker", vertx.getOrCreateContext().deploymentID()));
        report.start();

        JsonObject structures = config().getJsonObject("structures");
        JsonObject connections = config().getJsonObject("connections", new JsonObject());
        if (structures.isEmpty()) {
            log.info(String.format("[Worker@%s] Stopping PMB amass worker: empty structures", vertx.getOrCreateContext().deploymentID()));
            log.info(report.end().generate());
            vertx.undeploy(vertx.getOrCreateContext().deploymentID());
            return;
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (String uai : structures.fieldNames()) {
            // Chaque établissement a son propre serveur PMB : on (ré)enregistre son instance
            // à partir de la configuration stockée en base avant de l'interroger. Un
            // établissement pas encore paramétré côté admin PMB est simplement ignoré, il
            // ne doit pas faire échouer l'amass des autres établissements.
            if (PMBServer.register(vertx, uai, connections.getJsonObject(uai)) == null) {
                log.info(String.format("Skipping %s: no PMB connection configured", uai));
                continue;
            }
            Promise<Void> promise = Promise.promise();
            log.info(String.format("Amassing %s bibliographic record", uai));
            amass(uai, structures.getString(uai), promise);
            futures.add(promise.future());
        }
        if (futures.isEmpty()) {
            report.end();
            log.info(report.generate().encodePrettily());
            log.info(String.format("[Worker@%s] Stopping PMB amass worker: no configured structure", vertx.getOrCreateContext().deploymentID()));
            vertx.undeploy(vertx.getOrCreateContext().deploymentID());
            return;
        }

        Future.any(futures).onComplete(ar -> {
            report.end();
            log.info(report.generate().encodePrettily());
            log.info(String.format("[Worker@%s] Stopping PMB amass worker", vertx.getOrCreateContext().deploymentID()));
            vertx.undeploy(vertx.getOrCreateContext().deploymentID());
        });
    }

    /**
     * Amass bibliographic records based on given structure
     *
     * @param uai         Structure uai
     * @param structureId Structure identifier
     * @param handler     Function handler returning data
     */
    private void amass(String uai, String structureId, Handler<AsyncResult<Void>> handler) {
        new PMBSimpleSearch("*", uai).execute(ar -> {
            if (ar.failed()) {
                log.error(String.format("Failed to execute simple search for structure %s", uai));
                handler.handle(Future.failedFuture(uai));
                return;
            }

            String searchId = ar.result().getJsonObject("result", new JsonObject()).getString("searchId");
            int recordsCount = Integer.parseInt(ar.result().getJsonObject("result", new JsonObject()).getString("nbResults"));
            PMBFetchSearchRecords fetch = new PMBFetchSearchRecords()
                    .setUAI(uai)
                    .setSearchId(searchId)
                    .setRecorsCount(recordsCount);
            processPage(fetch, structureId, handler);
        });
    }

    private void processPage(PMBFetchSearchRecords fetch, String structureId, Handler<AsyncResult<Void>> handler) {
        fetch.next(fr -> {
            if (fr.failed()) {
                log.error(String.format("Failed to fetch next page for structure %s and search id %s", fetch.uai(), fetch.searchId()), fr.cause());
                handler.handle(Future.failedFuture(fr.cause()));
            } else {
                List<LinkedHashMap> result = fr.result().getJsonArray("result", new JsonArray()).getList();
                // If result is not empty, map result and send it to mediacentre then process next page
                if (!result.isEmpty()) {
                    List<JsonObject> records = result.stream()
                            .map(instruction -> new BibliographicRecord(instruction, fetch.uai()))
                            .map(BibliographicRecord::toJSON)
                            .collect(Collectors.toList());
                    records.forEach(record -> record.put("structure", structureId));
                    report.incrementStructureCount(fetch.uai(), records.size());
                    sendBibliographicRecordToMediacentre(records);
                    processPage(fetch, structureId, handler);
                } else {
                    // Else stop process
                    handler.handle(Future.succeededFuture());
                }
            }
        });
    }

    private void sendBibliographicRecordToMediacentre(List<JsonObject> records) {
        vertx.eventBus().send("fr.openent.mediacentre.source.PMB|records", new JsonArray(records));
    }
}
