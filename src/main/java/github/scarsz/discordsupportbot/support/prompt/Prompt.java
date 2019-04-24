package github.scarsz.discordsupportbot.support.prompt;

import github.scarsz.discordsupportbot.Application;
import github.scarsz.discordsupportbot.sql.Database;
import org.codejargon.fluentjdbc.api.mapper.Mappers;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class Prompt {

    private final UUID id;

    public Prompt(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return Database.query().select("SELECT title FROM prompts WHERE id = ?").params(id)
                .firstResult(Mappers.singleString()).get();
    }

    public String getDescription() {
        return Database.query().select("SELECT description FROM prompts WHERE id = ?").params(id)
                .firstResult(Mappers.singleString()).get();
    }

    public Map getData() {
        return Database.query().select("SELECT data FROM prompts WHERE id = ?").params(id)
                .firstResult(rs -> Application.get().getGson().fromJson(rs.getString("data"), Map.class))
                .orElse(null);
    }

    public static List<Prompt> collect(UUID helpdesk) {
        return new LinkedList<>(
                Database.query().select("SELECT id, type FROM prompts WHERE helpdesk = ?")
                        .params(helpdesk)
                        .setResult(rs -> {
                            switch (rs.getInt("type")) {
                                case 0: default: return new FreeResponsePrompt(UUID.fromString(rs.getString("id")));
                                case 1: return new MultipleChoicePrompt(UUID.fromString(rs.getString("id")));
                            }
                        })
        );
    }

}
