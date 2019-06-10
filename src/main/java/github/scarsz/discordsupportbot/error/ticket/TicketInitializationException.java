package github.scarsz.discordsupportbot.error.ticket;

import java.util.UUID;

public abstract class TicketInitializationException extends Throwable {

    private final UUID ticket;

    public TicketInitializationException(UUID ticket) {
        this.ticket = ticket;
    }

    public TicketInitializationException(UUID ticket, String message) {
        super(message);
        this.ticket = ticket;
    }

    public UUID getTicket() {
        return ticket;
    }

}
