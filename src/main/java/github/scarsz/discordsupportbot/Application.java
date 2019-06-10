package github.scarsz.discordsupportbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import github.scarsz.discordsupportbot.discord.Bot;
import github.scarsz.discordsupportbot.discord.LoggingHandler;
import github.scarsz.discordsupportbot.sql.Database;
import github.scarsz.discordsupportbot.www.Http;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Application extends Thread {

    private static Application instance = null;

    private final Logger logger = LoggerFactory.getLogger(Application.class);
    private final LoggingHandler loggingHandler;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ScheduledExecutorService service = Executors.newScheduledThreadPool(4);
    private Database database;
    private Bot bot;
    private Http http;

    public Application(String token, String secret) {
        loggingHandler = new LoggingHandler(this);
        java.util.logging.Logger.getLogger("").addHandler(loggingHandler);

        logger.info("Initializing support bot application");

        // if a previous instance exists already, shut it down and wait for completion
        if (instance != null) {
            logger.info("Previous application instance found, shutting it down...");
            instance.start();
            try {
                instance.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Finished shutting previous instance down");
        }
        instance = this;
        logger.info("Instance set");

        try {
            Runtime.getRuntime().addShutdownHook(this);
            logger.info("Added runtime shutdown hook");
        } catch (Exception e) {
            logger.error("Failed to add shutdown hook, not starting");
            System.exit(2);
        }

        try {
            database = new Database();
        } catch (SQLException | ClassNotFoundException e) {
            logger.error("Failed to initialize database");
            e.printStackTrace();
            System.exit(3);
        }

        try {
            bot = new Bot(token);
            bot.init();
        } catch (Exception e) {
            logger.error("Failed to connect to Discord");
            e.printStackTrace();
            System.exit(4);
        }

        try {
            http = new Http(bot.getJda().getSelfUser().getId(), secret);
        } catch (Exception e) {
            logger.error("Failed to initialize HTTP server");
            e.printStackTrace();
            System.exit(5);
        }

        logger.info("Completely finished initialization");

//        try {
//            TextChannel category = bot.getJda().getTextChannelById("566883579796389888");
//            Helpdesk helpdesk = Helpdesk.create(category);
//            logger.info("Made helpdesk " + helpdesk);
//        } catch (SQLException | HelpdeskInitializationException e) {
//            e.printStackTrace();
//        }

        logger.info("Bot is in " + bot.getJda().getGuilds().size() + " guilds");
    }

    /**
     * This method runs <strong>only</strong> as a result of the JVM starting shutdown hooks.
     */
    @Override
    public void run() {
        if (bot != null) {
            bot.shutdown();
        }

        if (http != null) {
            http.shutdown();
        }

        // this is sent to System.out because if it goes thru SLF4J, the message doesn't show until after the JVM dies
        System.out.println("Finished shutdown sequence");
    }

    public static Application get() {
        return instance;
    }

    public Database getDatabase() {
        return database;
    }

    public Gson getGson() {
        return gson;
    }

    public Bot getBot() {
        return bot;
    }

    public Http getHttp() {
        return http;
    }

    public LoggingHandler getLoggingHandler() {
        return loggingHandler;
    }

    public ScheduledExecutorService getService() {
        return service;
    }

}
