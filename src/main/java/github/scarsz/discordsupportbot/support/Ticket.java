package github.scarsz.discordsupportbot.support;

import github.scarsz.discordsupportbot.Application;
import github.scarsz.discordsupportbot.discord.Bot;
import github.scarsz.discordsupportbot.error.TicketInitializationException;
import github.scarsz.discordsupportbot.sql.Database;
import github.scarsz.discordsupportbot.util.TimeUtil;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.codejargon.fluentjdbc.api.mapper.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Ticket extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    private final List<ScheduledFuture> warnings = new ArrayList<>();
    private final Helpdesk helpdesk;
    private final UUID id;

    public Ticket(Helpdesk helpdesk, UUID id) throws TicketInitializationException {
        this.helpdesk = helpdesk;
        this.id = id;

        if (getChannel() == null) throw new TicketInitializationException(id, "Ticket channel doesn't exist");
        if (getAuthor() == null) throw new TicketInitializationException(id, "Author is not in server anymore");

        Application.get().getBot().getJda().addEventListener(this);

        setInactivityWarnings();
    }

    public static Ticket create(Helpdesk helpdesk, User author) {
        Objects.requireNonNull(helpdesk);
        Objects.requireNonNull(author);

        int count = Database.get(helpdesk.getId(), "helpdesks", "ticker");
        TextChannel channel = (TextChannel) helpdesk.getCategory().createTextChannel(String.valueOf(count))
                .addPermissionOverride(helpdesk.getChannel().getGuild().getMember(author), Permission.ALL_TEXT_PERMISSIONS, 0).complete();
        return create(helpdesk, author, channel);
    }

    public static Ticket create(Helpdesk helpdesk, User author, TextChannel channel) {
        Objects.requireNonNull(helpdesk);
        Objects.requireNonNull(author);
        Objects.requireNonNull(channel);

        UUID ticketId = UUID.fromString(Database.query().update("INSERT INTO tickets () VALUES (?, ?, ?)")
                .params(helpdesk.getId(), author.getId(), channel.getId())
                .runFetchGenKeys(Mappers.singleString()).firstKey().get());
        try {
            Ticket ticket = new Ticket(helpdesk, ticketId);
            helpdesk.getTickets().add(ticket);
            return ticket;
        } catch (TicketInitializationException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (!event.getChannel().equals(getChannel())) return;
        processMessage(event);
    }

    public void processMessage(GuildMessageReceivedEvent event) {
        boolean isAuthor = event.getAuthor().equals(getAuthor());
        boolean privileged = isAuthor || helpdesk.memberIsHelper(event);

        if (isAuthor) {
            setStatus(Status.RESPONDED);
        } else if (privileged) {
            setStatus(Status.AWAITING_RESPONSE);
        }

        String message = event.getMessage().getContentDisplay() + (
                event.getMessage().getAttachments().size() > 0
                        ? " " + event.getMessage().getAttachments().stream()
                        : ""
        );
        Database.query().update("INSERT INTO MESSAGES (ID, TICKET, CONTENT, AUTHOR) VALUES (?, ?, ?, ?)")
                .params(
                        event.getMessageId(),
                        this.id,
                        message,
                        !event.getAuthor().isFake()
                                ? event.getAuthor().getId()
                                : event.getAuthor().getName()
                ).run();
    }

    @Override
    public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
        if (!event.getChannel().equals(getChannel())) return;
        Database.query().update("UPDATE MESSAGES SET PURE = 1 WHERE ID = ?")
                .params(event.getMessageId()).run();
    }

    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
        if (!event.getChannel().equals(getChannel())) return;
        Database.query().update("UPDATE MESSAGES SET PURE = 2 WHERE ID = ?")
                .params(event.getMessageId()).run();
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (!event.getChannel().equals(getChannel())) return;
        if (event.getUser().isBot()) return;

        boolean isAuthor = event.getUser().equals(getAuthor());
        boolean privileged = isAuthor || helpdesk.memberIsHelper(event);

        if (event.getReactionEmote().getName().equals("solved")) {
            if (privileged && getStatus() != Status.SOLVED) {
                getChannel().sendMessage(new EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTitle("Ticket has been marked as solved!")
                        .setFooter(event.getUser().getName(), event.getUser().getEffectiveAvatarUrl())
                        .build()).queue(message -> close());
            } else {
                event.getReaction().removeReaction(event.getUser()).queue();
            }
        }
    }

    private void close() {
        close(1, TimeUnit.MINUTES);
    }

    private void close(int delay, TimeUnit unit) {
        Application.get().getService().schedule(this::closeNow, delay, unit);
    }

    private void closeNow() {
        warnings.forEach(scheduledFuture -> scheduledFuture.cancel(false));
        Bot.get().getJda().removeEventListener(this);
        helpdesk.getTickets().remove(this);
        getChannel().delete().queue();

        boolean shouldSend = Database.get(id, "helpdesks", "sendtranscripts");
        if (shouldSend) {
            //TODO
        }
    }

    private void setInactivityWarnings() {
        warnings.forEach(scheduledFuture -> scheduledFuture.cancel(false));
        warnings.clear();

        Long creationTime = getTime();
        if (creationTime != null) {
            long expirationTime = TimeUnit.MINUTES.toMillis(helpdesk.getConfig().getExpiration());
            long expiration1 = (long) ((creationTime + (expirationTime * 0.75)) - System.currentTimeMillis());
            long expiration2 = (long) ((creationTime + (expirationTime * 0.9)) - System.currentTimeMillis());
            long expiration = ((creationTime + expirationTime) - System.currentTimeMillis());
            if (expiration1 > 0) {
                warnings.add(Application.get().getService().schedule(() -> {
                    getChannel().sendMessage(getAuthor().getAsMention()).embed(new EmbedBuilder()
                            .setColor(Color.YELLOW)
                            .setTitle("Your ticket has been responded to.")
                            .setDescription("If no response is given within " + TimeUtil.getDurationBreakdown(expiration) + ", the ticket will be closed due to inactivity.")
                            .build()
                    ).queue();
                }, expiration1, TimeUnit.MILLISECONDS));
            }

            if (expiration2 > 0) {
                warnings.add(Application.get().getService().schedule(() -> {
                    getChannel().sendMessage(getAuthor().getAsMention()).embed(new EmbedBuilder()
                            .setColor(Color.YELLOW)
                            .setTitle("Your ticket has been responded to.")
                            .setDescription("If no response is given within " + TimeUtil.getDurationBreakdown(expiration) + ", the ticket will be closed due to inactivity.")
                            .build()
                    ).queue();
                }, expiration2, TimeUnit.MILLISECONDS));
            }

            warnings.add(Application.get().getService().schedule(() -> {
                getChannel().sendMessage(getAuthor().getAsMention()).embed(new EmbedBuilder()
                        .setColor(Color.RED)
                        .setTitle("Your ticket is closing due to inactivity.")
                        .build()
                ).queue();
                close();
            }, expiration, TimeUnit.MILLISECONDS));
        }
    }

    // getters/setters

    public String getAuthorId() {
        return Database.get(id, "tickets", "author");
    }
    public User getAuthor() {
        String id = getAuthorId();
        return id != null
                ? Application.get().getBot().getJda().getUserById(id)
                : null;
    }
    public void setAuthor(String id) {
        Database.update(this.id, "tickets", "author", id);
    }
    public void setAuthor(User user) {
        setAuthor(user.getId());
    }
    public void setAuthor(Member member) {
        setAuthor(member.getUser().getId());
    }

    public String getChannelId() {
        return Database.get(id, "tickets", "channel");
    }
    public TextChannel getChannel() {
        String id = getChannelId();
        return id != null
                ? Application.get().getBot().getJda().getTextChannelById(id)
                : null;
    }
    public void setChannel(String id) {
        Database.update(this.id, "tickets", "channel", id);
    }
    public void setChannel(TextChannel channel) {
        setChannel(channel.getId());
    }

    public Status getStatus() {
        return Status.valueOf(Database.get(id, "tickets", "status"));
    }
    public void setStatus(Status status) {
        if (getStatus().equals(Status.SOLVED)) return;
        Database.update(id, "tickets", "status", status.name());
    }

    public Long getTime() {
        return Database.get(id, "tickets", "time");
    }
    public void setTime(long time) {
        Database.update(id, "tickets", "time", time);
    }

    public UUID getId() {
        return id;
    }

    public static List<Ticket> collect(UUID helpdesk) {
        return helpdesk != null ? collect(Bot.get().getHelpdesk(helpdesk)) : null;
    }

    public static List<Ticket> collect(Helpdesk helpdesk) {
        List<Ticket> tickets = new LinkedList<>(
                Database.query().select("SELECT id FROM tickets WHERE helpdesk = ?")
                        .params(helpdesk.getId())
                        .setResult(rs -> {
                            try {
                                return new Ticket(helpdesk, UUID.fromString(rs.getString("id")));
                            } catch (TicketInitializationException e) {
                                Bot.get().getLogger().error("Failed to create ticket " + e.getTicket() + ": " + e.getMessage());
                                return null;
                            }
                        })
        );
        tickets.remove(null);
        return tickets;
    }

}
