package github.scarsz.discordsupportbot.support;

import github.scarsz.discordsupportbot.Application;
import github.scarsz.discordsupportbot.sql.Database;
import net.dv8tion.jda.core.entities.Category;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class Configuration {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    private final UUID uuid;

    public Configuration(UUID helpdesk) {
        this.uuid = helpdesk;
    }

    private static void set(UUID helpdesk, Object... parameters) {
        if (parameters.length % 2 != 0) throw new IllegalArgumentException("Parameter array length must be divisible by 2");
        List<String> segments = new LinkedList<>();
        for (int i = 0; i < parameters.length; i += 2) segments.add(parameters[i] + " = ?");
        String complete = String.join(", ", segments);

        try {
            PreparedStatement statement = Database.sql("UPDATE helpdesks SET " + complete + " WHERE uuid = ?");
            for (int i = 0; i < parameters.length / 2; i++) {
                statement.setObject(i + 1, parameters[(i * 2) + 1]);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to batch set values for " + helpdesk + ": " + e.getMessage(), e);
        }
    }

    public Category getCategory() {
        String categoryId = getCategoryId();
        return StringUtils.isNumeric(categoryId)
                ? Application.get().getBot().getJda().getCategoryById(categoryId)
                : null;
    }
    public String getCategoryId() {
        return Database.get(uuid, "helpdesks", "category");
    }
    public void setCategory(String id) {
        set(uuid, "category", id);
    }
    public void setCategory(Category category) {
        setCategory(category.getId());
    }

    public TextChannel getChannel() {
        String textChannelId = getChannelId();
        return StringUtils.isNumeric(textChannelId)
                ? Application.get().getBot().getJda().getTextChannelById(textChannelId)
                : null;
    }
    public String getChannelId() {
        return Database.get(uuid, "helpdesks", "channel");
    }
    public void setChannel(String id) {
        set(uuid, "channel", id);
    }
    public void setChannel(TextChannel category) {
        setCategory(category.getId());
    }

    public Role getRole() {
        String roleId = getRoleId();
        return StringUtils.isNumeric(roleId)
                ? Application.get().getBot().getJda().getRoleById(roleId)
                : null;
    }
    public String getRoleId() {
        return Database.get(uuid, "helpdesks", "role");
    }
    public void setRole(String id) {
        set(uuid, "role", id);
    }
    public void setRole(Role role) {
        setRole(role.getId());
    }

    public Integer getExpiration() {
        return Database.get(uuid, "helpdesks", "expiration");
    }
    public void setExpiration(int minutes) {
        set(uuid, "helpdesks", minutes);
    }

}
