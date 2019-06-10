package github.scarsz.discordsupportbot.support;

import github.scarsz.discordsupportbot.discord.Bot;
import github.scarsz.discordsupportbot.error.HelpdeskInitializationException;
import github.scarsz.discordsupportbot.sql.Database;
import github.scarsz.discordsupportbot.support.prompt.Prompt;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Category;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.codejargon.fluentjdbc.api.mapper.Mappers;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Helpdesk extends ListenerAdapter {

    public static Helpdesk create(Category category) throws HelpdeskInitializationException {
        if (category.getTextChannels().size() == 0) {
            throw new IllegalArgumentException("Given category " + category + " doesn't contain at least one text channel");
        }

        return create(category, category.getTextChannels().get(0));
    }

    public static Helpdesk create(TextChannel channel) throws HelpdeskInitializationException {
        if (channel.getParent() == null) {
            throw new IllegalArgumentException("Given text channel " + channel + " doesn't have a parent");
        }

        return create(channel.getParent(), channel);
    }

    public static Helpdesk create(Category category, TextChannel channel) throws HelpdeskInitializationException {
        UUID uuid = UUID.fromString(Database.query().update("insert into helpdesks (category, channel) values (?, ?)")
                .params(category.getId(), channel.getId())
                .runFetchGenKeys(Mappers.singleString()).firstKey().get());
        return new Helpdesk(uuid);
    }

    private final UUID id;
    private final Configuration config;
    private final Set<Ticket> tickets;
    private final Map<String, UUID> recentTickets;

    public Helpdesk(UUID id) throws HelpdeskInitializationException {
        this.id = id;
        this.config = new Configuration(id);
        recentTickets = new HashMap<>();

        Category category = config.getCategory();
        if (category == null) throw new HelpdeskInitializationException(id, "Category " + config.getCategoryId() + " not found");
        TextChannel channel = config.getChannel();
        if (channel == null) throw new HelpdeskInitializationException(id, "Channel " + config.getChannelId() + " not found");

        this.tickets = new HashSet<>(Ticket.collect(this));

        Bot.get().getJda().addEventListener(this);
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (!event.getChannel().equals(config.getChannel())) return;
        if (event.getAuthor().isFake() || event.isWebhookMessage() || event.getAuthor().isBot()) return;
        if (event.getMessage().getContentRaw().equals(".") && (event.getMember().hasPermission(Permission.ADMINISTRATOR) || memberIsHelper(event.getMember()))) return;
        if (recentTickets.containsKey(event.getAuthor().getId())) {
            tickets.stream().filter(t -> t.getId().equals(recentTickets.get(event.getAuthor().getId())))
                    .findFirst().ifPresent(t -> t.processMessage(event));
            event.getMessage().delete().queue();
            return;
        }

        Ticket ticket = Ticket.create(this, event.getAuthor());
        String message = event.getMessage().getContentDisplay() + (
                event.getMessage().getAttachments().size() > 0
                        ? " " + event.getMessage().getAttachments().stream()
                        : ""
        );
        Database.query().update("INSERT INTO MESSAGES (ID, TICKET, CONTENT, AUTHOR) VALUES (?, ?, ?, ?)")
                .params(
                        event.getMessageId(),
                        ticket.getId(),
                        message,
                        !event.getAuthor().isFake()
                                ? event.getAuthor().getId()
                                : event.getAuthor().getName()
                ).run();

        recentTickets.put(event.getAuthor().getId(), ticket.getId());
        event.getMessage().delete().queue(v -> event.getChannel().sendMessage(event.getAuthor().getAsMention()).embed(new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("A ticket has been made for your query!")
                .setDescription("Direct further messages to " + ticket.getChannel().getAsMention() + ".")
                .build()
        ).queue(
                m -> m.delete().queueAfter(30, TimeUnit.SECONDS,
                        v2 -> recentTickets.remove(event.getAuthor().getId())
                )
        ));
    }

    public boolean memberIsHelper(Member member) {
        return config.getRole() != null ? member.getRoles().contains(config.getRole()) : member.hasPermission(Permission.ADMINISTRATOR);
    }

    public boolean memberIsHelper(GuildMessageReceivedEvent event) {
        return event.getAuthor().isBot() || event.getAuthor().isFake() || this.memberIsHelper(event.getMember());
    }

    public boolean memberIsHelper(GuildMessageReactionAddEvent event) {
        return event.getUser().isBot() || event.getUser().isFake() || this.memberIsHelper(event.getMember());
    }

    public UUID getId() {
        return id;
    }

    public Category getCategory() {
        return config.getCategory();
    }

    public TextChannel getChannel() {
        return config.getChannel();
    }

    public List<Prompt> getPrompts() {
        return Prompt.collect(this.id);
    }

    public Set<Ticket> getTickets() {
        return tickets;
    }

    public Configuration getConfig() {
        return config;
    }

    public static List<Helpdesk> collect() {
        Set<Helpdesk> helpdesks = Database.query().select("SELECT id FROM helpdesks").setResult(
                resultSet -> {
                    try {
                        return new Helpdesk(UUID.fromString(resultSet.getString("id")));
                    } catch (HelpdeskInitializationException e) {
                        Bot.get().getLogger().error("Failed to create helpdesk " + e.getHelpdesk() + ": " + e.getMessage());
                        return null;
                    }
                }
        );
        List<Helpdesk> list = new LinkedList<>(helpdesks);
        list.remove(null);
        return list;
    }
}
