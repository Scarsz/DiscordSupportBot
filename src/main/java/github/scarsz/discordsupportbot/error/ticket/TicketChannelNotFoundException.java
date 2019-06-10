package github.scarsz.discordsupportbot.error.ticket;

import java.util.UUID;

public class TicketChannelNotFoundException extends TicketInitializationException {

    public TicketChannelNotFoundException(UUID id) {
        super(id);
    }

}
