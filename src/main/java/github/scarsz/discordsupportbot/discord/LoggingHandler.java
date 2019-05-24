package github.scarsz.discordsupportbot.discord;

import github.scarsz.discordsupportbot.Application;
import github.scarsz.discordsupportbot.util.TimeUtil;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageHistory;
import net.dv8tion.jda.core.entities.TextChannel;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

public class LoggingHandler extends Handler {

    private final Application application;
    private final List<LogRecord> queue = new LinkedList<>();

    public LoggingHandler(Application application) {
        this.application = application;
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::flush, 1, 1, TimeUnit.SECONDS);
    }

    private void purgeChannel() {
        MessageHistory history = getChannel().getHistory();

        while (true) {
            List<Message> messages = history.retrievePast(100).complete();
            if (messages.size() == 0) {
                break;
            } else {
                List<Message> oldMessages = new ArrayList<>();
                for (Message message : messages) {
                    long created = message.getCreationTime().toInstant().toEpochMilli();
                    if (System.currentTimeMillis() - created > TimeUnit.DAYS.toMillis(14)) {
                        // message is over the time limit for mass deleting messages
                        message.delete().queue();
                        oldMessages.add(message);
                    }
                }
                messages.removeAll(oldMessages);

                if (messages.size() == 1) {
                    messages.get(0).delete().queue();
                } else {
                    getChannel().deleteMessages(messages).queue();
                }
            }
        }
    }

    private boolean wasReady = false;
    public boolean isReady() {
        boolean status = application.getBot() != null && application.getBot().getJda().getStatus() == JDA.Status.CONNECTED;
        if (status && !wasReady) {
            wasReady = true;
            purgeChannel();
        }
        return status;
    }

    public TextChannel getChannel() {
        return isReady() ? application.getBot().getJda().getTextChannelById("549492346706984960") : null;
    }

    private static final Map<String, Function<String, String>> LOG_MAPPINGS = new LinkedHashMap<String, Function<String, String>>() {{
        put("spark", clazz -> "Spark");
        put("org.eclipse.jetty", clazz -> "Jetty");
        put("net.dv8tion.jda", clazz -> "JDA");
        put("github.scarsz.discordsupportbot.www.Http", clazz -> "WWW");
        put("github.scarsz.discordsupportbot", clazz -> {
            String[] split = clazz.split("\\.");
            return split[split.length - 1];
        });
    }};
    public String format(LogRecord record) {
        String symbol = record.getLevel() == Level.INFO
                ? "|"
                : record.getLevel() == Level.WARNING
                        ? "+"
                        : record.getLevel() == Level.SEVERE
                                ? "-"
                                : "!";

        Function<String, String> function = LOG_MAPPINGS.entrySet().stream()
                .filter(entry -> record.getSourceClassName().startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
        String loggerName = function != null ? function.apply(record.getSourceClassName()) : record.getSourceClassName();

        return symbol + " [" + TimeUtil.timestamp(record.getMillis()) + "] " + loggerName + " > " + record.getMessage();
    }

    public List<String> build(List<LogRecord> records) {
        if (records.size() == 0) return Collections.singletonList("```\n```");

        Level level = records.get(0).getLevel();
        String language = level == Level.INFO ? "yaml" : "diff";
        List<String> lines = records.stream().map(this::format).collect(Collectors.toList());

        List<String> result = new LinkedList<>();
        final String lead = "```" + language + "\n";
        StringBuilder builder = new StringBuilder(lead);
        while (lines.size() > 0) {
            String line = lines.remove(0);
            if (builder.length() + line.length() + "\n```".length() <= 2000) {
                builder.append(line).append("\n");
            } else {
                result.add(builder.append("```").toString());
                builder = new StringBuilder(lead);
            }
        }
        if (builder.length() > lead.length()) result.add(builder.append("```").toString());
        if (level == Level.SEVERE) result.add("<@95088531931672576>");
        return result;
    }

    @Override
    public void publish(LogRecord record) {
        queue.add(record);
    }

    @Override
    public void flush() {
        if (isReady()) {
            List<LogRecord> records = new LinkedList<>(queue);
            queue.clear();

            Level lastLevel = null;
            List<LogRecord> recordsInGroup = new LinkedList<>();
            for (LogRecord record : records) {
                if (lastLevel == null) {
                    lastLevel = record.getLevel();
                }

                if (lastLevel == record.getLevel()) {
                    recordsInGroup.add(record);
                } else {
                    build(recordsInGroup).forEach(s -> getChannel().sendMessage(s).queue());
                    recordsInGroup.clear();
                    recordsInGroup.add(record);
                    lastLevel = record.getLevel();
                }
            }

            if (recordsInGroup.size() > 0) {
                build(recordsInGroup).forEach(s -> getChannel().sendMessage(s).queue());
            }
        }
    }

    @Override
    public void close() throws SecurityException {}

}
