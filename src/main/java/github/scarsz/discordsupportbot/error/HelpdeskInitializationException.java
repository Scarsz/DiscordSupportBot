package github.scarsz.discordsupportbot.error;

import java.util.UUID;

public class HelpdeskInitializationException extends Throwable {

    private final UUID helpdesk;

    public HelpdeskInitializationException(UUID helpdesk, String message) {
        super(message);
        this.helpdesk = helpdesk;
    }

    public UUID getHelpdesk() {
        return helpdesk;
    }

}
