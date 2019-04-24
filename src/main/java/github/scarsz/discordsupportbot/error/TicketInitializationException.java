package github.scarsz.discordsupportbot.error;

import java.util.UUID;

public class TicketInitializationException extends Throwable {

    private final UUID ticket;

    public TicketInitializationException(UUID ticket, String message) {
        super(message);
        this.ticket = ticket;
    }

    public UUID getTicket() {
        return ticket;
    }

}
