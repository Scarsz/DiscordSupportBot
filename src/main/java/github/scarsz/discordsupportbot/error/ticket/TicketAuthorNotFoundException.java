package github.scarsz.discordsupportbot.error.ticket;

import java.util.UUID;

public class TicketAuthorNotFoundException extends TicketInitializationException {

    private final String authorId;

    public TicketAuthorNotFoundException(UUID id, String authorId) {
        super(id);
        this.authorId = authorId;
    }

    public String getAuthorId() {
        return authorId;
    }

}
