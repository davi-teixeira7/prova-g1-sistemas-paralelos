package storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {
    private AppPaths() {
    }

    public static synchronized Path dataDir() throws IOException {
        Path cwd = Paths.get("").toAbsolutePath().normalize();

        Path currentSourceData = cwd.resolve("data");
        if (Files.isDirectory(currentSourceData) && isSourceDirectory(cwd)) {
            Files.createDirectories(currentSourceData);
            return currentSourceData;
        }

        Path nestedSourceData = cwd.resolve("src").resolve("data");
        if (Files.isDirectory(nestedSourceData)) {
            Files.createDirectories(nestedSourceData);
            return nestedSourceData;
        }

        if (isSourceDirectory(cwd)) {
            Files.createDirectories(currentSourceData);
            return currentSourceData;
        }

        Files.createDirectories(nestedSourceData);
        return nestedSourceData;
    }

    public static synchronized Path usersJsonFile() throws IOException {
        Path usersFile = dataDir().resolve("users.json");
        if (Files.notExists(usersFile)) {
            Files.writeString(usersFile, "[]\n", StandardCharsets.UTF_8);
        }
        return usersFile;
    }

    public static synchronized Path auctionLogFile() throws IOException {
        Path logFile = dataDir().resolve("leilao.log");
        if (Files.notExists(logFile)) {
            Files.createFile(logFile);
        }
        return logFile;
    }

    private static boolean isSourceDirectory(Path directory) {
        return Files.exists(directory.resolve("AuctionServer.java"))
                || Files.exists(directory.resolve("AuctionServer.class"));
    }
}
