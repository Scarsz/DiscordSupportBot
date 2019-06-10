package github.scarsz.discordsupportbot.sql;

import github.scarsz.discordsupportbot.Application;
import org.codejargon.fluentjdbc.api.FluentJdbcBuilder;
import org.codejargon.fluentjdbc.api.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class Database {

    private static final File FILE = new File("support");

    private final Logger logger = LoggerFactory.getLogger(Database.class);
    private final Connection connection;
    private final Query query;

    public Database() throws ClassNotFoundException, SQLException {
        Class.forName("org.h2.Driver");
        connection = DriverManager.getConnection("jdbc:h2:" + FILE.getAbsolutePath());
        query = new FluentJdbcBuilder()
                .connectionProvider(q -> q.receive(connection))
                .afterQueryListener(details -> {
                    if (details.success()) {
                        //logger.info(details.executionTimeMs() + "ms > " + details.sql());
                    } else {
                        logger.error(details.executionTimeMs() + "ms > " + details.sql());
                        details.sqlException().ifPresent(Throwable::printStackTrace);
                    }
                })
                .build().query();

        query.update("create table if not exists HELPDESKS\n" +
                "(\n" +
                "    ID                UUID        default RANDOM_UUID() not null,\n" +
                "    CATEGORY          VARCHAR(32)                       not null,\n" +
                "    CHANNEL           VARCHAR(32)                       not null,\n" +
                "    TICKER            INTEGER     default 0             not null,\n" +
                "    SENDTRANSCRIPTS   BOOLEAN     default TRUE          not null,\n" +
                "    ROLE              VARCHAR(32) default NULL,\n" +
                "    EXPIRATION        INTEGER     default NULL,\n" +
                "    TRANSCRIPTCHANNEL VARCHAR(32) default NULL,\n" +
                "    PINGAUTHOR        BOOLEAN     default TRUE          not null,\n" +
                "    LIVETOPIC         BOOLEAN     default TRUE          not null,\n" +
                "    HOURS             VARCHAR(97) default NULL,\n" +
                "    constraint HELPDESKS_PK primary key (ID),\n" +
                "    constraint HELPDESKS_CATEGORY_UINDEX unique (CATEGORY),\n" +
                "    constraint HELPDESKS_CHANNEL_UINDEX unique (CHANNEL)\n" +
                ");").run();
        query.update("create table if not exists MESSAGES\n" +
                "(\n" +
                "    ID      VARCHAR(32)                                          not null,\n" +
                "    TICKET  UUID                                                 not null,\n" +
                "    CONTENT CLOB                                                 not null,\n" +
                "    AUTHOR  VARCHAR(32)                                          not null,\n" +
                "    TIME    TIMESTAMP WITH TIME ZONE default current_timestamp() not null,\n" +
                "    PURE    INTEGER default 0                                    not null,\n" +
                "    constraint MESSAGES_PK primary key (ID)\n" +
                ");").run();
        query.update("create table if not exists PROMPTS\n" +
                "(\n" +
                "    ID          UUID default RANDOM_UUID() not null,\n" +
                "    HELPDESK    UUID                       not null,\n" +
                "    TYPE        INTEGER                    not null,\n" +
                "    TITLE       VARCHAR(256)               not null,\n" +
                "    DESCRIPTION VARCHAR(1024)              not null,\n" +
                "    DATA        CLOB,\n" +
                "    constraint PROMPTS_PK primary key (ID)\n" +
                ");").run();
        query.update("create table if not exists PROMPTS_RESPONSES\n" +
                "(\n" +
                "    ID       UUID default RANDOM_UUID() not null,\n" +
                "    TICKET   UUID                       not null,\n" +
                "    PROMPT   UUID                       not null,\n" +
                "    RESPONSE VARCHAR(1024)              not null,\n" +
                "    constraint PROMPTS_RESPONSES_PK primary key (ID)\n" +
                ");").run();
        query.update("create table if not exists TICKETS\n" +
                "(\n" +
                "    ID       UUID                     default RANDOM_UUID()       not null,\n" +
                "    HELPDESK UUID                                                 not null,\n" +
                "    CHANNEL  VARCHAR(32)                                          not null,\n" +
                "    TICKET   INTEGER                  default 0                   not null,\n" +
                "    AUTHOR   VARCHAR(32)                                          not null,\n" +
                "    STATUS   INTEGER                  default 0                   not null,\n" +
                "    TIME     TIMESTAMP WITH TIME ZONE default CURRENT_TIMESTAMP() not null,\n" +
                "    FROZEN   BOOLEAN                  default FALSE               not null,\n" +
                "    constraint TICKETS_PK primary key (ID)\n" +
                ");").run();

        logger.info("Finished database initialization");
    }

    public static PreparedStatement sql(String sql, Object... parameters) throws SQLException {
        PreparedStatement statement = Application.get().getDatabase().connection.prepareStatement(sql);
        for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
        return statement;
    }

    public static <T> T get(UUID id, String table, String column) {
//        return query().select("SELECT " + column + " FROM " + table + " WHERE id = ?")
//                .params(id).firstResult(rs -> {
//                    Object object = rs.getObject(1);
//                    return object != null ? (T) object : null;
//                }).orElse(null);

        try {
            ResultSet result = Database.sql("SELECT `" + column + "` FROM `" + table + "` WHERE `id` = ?", id).executeQuery();
            if (result.next()) {
                //noinspection unchecked
                return (T) result.getObject(column);
            } else {
                return null;
            }
        } catch (SQLException e) {
            Application.get().getDatabase().logger.error("Failed to retrieve " + column + " for " + id + " in " + table + ": " + e.getMessage(), e);
            return null;
        }
    }

    public static void update(UUID uuid, String table, String column, Object value) {
        query().update("UPDATE `" + table + "` SET " + column + " = ? WHERE `id` = ?")
                .params(value, uuid).run();

//        try {
//            Database.sql("UPDATE `" + table + "` SET " + column + " = ? WHERE `id` = ?", value, uuid).executeUpdate();
//        } catch (SQLException e) {
//            Application.get().getDatabase().logger.error("Failed to update " + column + " for " + uuid + " in " + table + ": " + e.getMessage(), e);
//        }
    }

    public static Query query() {
        return Application.get().getDatabase().query;
    }

    public static Connection connection() {
        return Application.get().getDatabase().connection;
    }

}
