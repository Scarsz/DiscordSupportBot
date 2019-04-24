package github.scarsz.discordsupportbot.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TimeUtil {

    private static Date date = new Date();
    private static SimpleDateFormat format = new SimpleDateFormat("EEE, d. MMM yyyy HH:mm:ss z");

    public static String timestamp() {
        return timestamp(System.currentTimeMillis());
    }

    public static String timestamp(long millis) {
        date.setTime(millis);
        return format.format(date);
    }

    public static String getDurationBreakdown(long millis) {
        if (millis < 0) throw new IllegalArgumentException("Duration must be greater than zero!");

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder(64);
        if (days > 0) sb.append(days).append(" days ");
        if (hours > 0) sb.append(hours).append(" hours ");
        if (minutes > 0) sb.append(minutes).append(" minutes ");
        if (seconds > 0) sb.append(seconds).append(" seconds");
        return(sb.toString());
    }

}

