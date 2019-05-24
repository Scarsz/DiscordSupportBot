package github.scarsz.discordsupportbot;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class Main {

    public static void main(String[] args) throws IOException {
        File tokenFile = new File(".token");
        String token = args.length >= 1 && StringUtils.isNotBlank(args[0])
                ? args[0]
                : tokenFile.exists()
                        ? FileUtils.readFileToString(tokenFile, Charset.forName("UTF-8"))
                                .replace("\n", "")
                                .replaceAll("/[^A-Za-z0-9.]/", "")
                        : "";
        File secretFile = new File(".secret");
        String secret = args.length >= 2 && StringUtils.isNotBlank(args[1])
                ? args[1]
                : secretFile.exists()
                        ? FileUtils.readFileToString(secretFile, Charset.forName("UTF-8"))
                                .replace("\n", "")
                                .replaceAll("/[^A-Za-z0-9.]/", "")
                        : "";

        if (StringUtils.isBlank(token)) {
            System.err.println("No bot token provided");
            System.exit(1);
        }

        if (StringUtils.isBlank(secret)) {
            System.err.println("No client secret provided");
            System.exit(1);
        }

        try {
            new Application(token, secret);
        } catch (Exception e) {
            System.err.println("Failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
