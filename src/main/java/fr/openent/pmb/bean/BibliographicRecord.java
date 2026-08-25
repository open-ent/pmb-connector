package fr.openent.pmb.bean;

import fr.openent.pmb.server.PMBServer;
import fr.openent.pmb.unimark.UniMarcField;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class BibliographicRecord {
    private List<String> authors = new ArrayList<>();
    private String description = "";
    private List<String> documentTypes = new ArrayList<>();
    private List<String> disciplines = new ArrayList<>();
    private List<String> editors = new ArrayList<>();
    private String id = "";
    private String image = "";
    private String isbn = "";
    private List<String> levels = new ArrayList<>();
    private String link = "";
    private List<String> metadata = new ArrayList<>();
    private String title = "";
    private final String uai;

    private Logger log = LoggerFactory.getLogger(BibliographicRecord.class);

    public BibliographicRecord(LinkedHashMap instruction, String uai) {
        this.uai = uai;
        JsonObject content = new JsonObject((String) instruction.getOrDefault("noticeContent", "{}"));
        JsonArray f = content.getJsonArray("f", new JsonArray());
        for (int i = 0; i < f.size(); i++) {
            JsonObject field = f.getJsonObject(i);
            String code = field.getString("c");
            if (UniMarcField.containsCode(code)) {
                if (field.containsKey("value")) parseSValue(UniMarcField.get(code), field.getString("value"));
                else parseSArray(UniMarcField.get(code), field.getJsonArray("s"));
            }
        }
    }

    private void parseSArray(UniMarcField field, JsonArray s) {
        for (int i = 0; i < s.size(); i++) {
            JsonObject value = s.getJsonObject(i);
            if (value.getValue("c").toString().equals(field.required())) {
                String sValue = value.getValue("value").toString();
                switch (field) {
                    case AUTHOR1:
                    case AUTHOR2:
                    case AUTHOR3:
                        this.authors.add(sValue);
                        break;
                    case DESCRIPTION:
                        this.description = sValue;
                        break;
                    case DOCUMENT_TYPE:
                        this.documentTypes.add(sValue);
                        break;
                    case EDITOR:
                        this.editors.add(sValue);
                        break;
                    case IMAGE:
                        this.image = sValue;
                        break;
                    case ISBN:
                        this.isbn = sValue;
                        break;
                    case LINK:
                        this.link = sValue;
                        break;
                    case METADATA:
                        this.metadata.add(sValue);
                        break;
                    case TITLE:
                        this.title = sValue;
                        break;
                    default:
                        log.error("Unable to find field in switch parseSArray");
                }
            }
        }

    }

    private void parseSValue(UniMarcField field, String s) {
        if (field == UniMarcField.ID) {
            this.id = s;
        } else {
            log.error("Unable to find field in parseSValue");
        }
    }


    public JsonObject toJSON() {
        return new JsonObject()
                .put("id", this.id)
                .put("authors", new JsonArray(this.authors))
                .put("description", this.description)
                .put("document_types", new JsonArray(this.documentTypes))
                .put("editors", new JsonArray(this.editors))
                .put("image", this.image)
                .put("isbn", this.isbn)
                .put("link", !this.link.trim().isEmpty() ? this.link : this.generateLink())
                .put("metadata", new JsonArray(this.metadata))
                .put("title", this.title);
    }

    private String generateLink() {
        PMBServer server = PMBServer.get(this.uai);
        return server == null ? "" : String.format("%s/index.php?lvl=notice_display&id=%s", server.host(), this.id);
    }
}
