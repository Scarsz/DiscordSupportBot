package github.scarsz.discordsupportbot.discord;

import github.scarsz.discordsupportbot.Application;
import github.scarsz.discordsupportbot.support.Helpdesk;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.util.List;
import java.util.UUID;

public class Bot extends ListenerAdapter {

    private final Logger logger = LoggerFactory.getLogger(Bot.class);
    private final JDA jda;
    private List<Helpdesk> helpdesks;

    public Bot(String token) throws LoginException, InterruptedException {
        logger.info("Bot token is " + token);
        jda = new JDABuilder()
                .setToken(token)
                .setEnableShutdownHook(false) // we have our own shutdown hook
                .addEventListener(this)
                .build().awaitReady();
    }

    public void init() {
        helpdesks = Helpdesk.collect();
        logger.info("We're serving " + helpdesks.size() + " helpdesks");

        logger.info("Finished JDA initialization");
    }

    public void shutdown() {
        if (jda != null && jda.getStatus() != JDA.Status.SHUTDOWN) {
            jda.getTextChannelById("549492346706984960").sendMessage(new EmbedBuilder().setColor(Color.RED).setTitle("Offline").build()).complete();
            jda.shutdown();
        }
    }

    public JDA getJda() {
        return jda;
    }

    public List<Helpdesk> getHelpdesks() {
        return helpdesks;
    }

    public Helpdesk getHelpdesk(UUID id) {
        return helpdesks.stream().filter(helpdesk -> helpdesk.getId().equals(id)).findFirst().orElse(null);
    }

    public static Bot get() {
        return Application.get().getBot();
    }

    public Logger getLogger() {
        return logger;
    }

}
