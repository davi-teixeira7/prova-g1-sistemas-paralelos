package auth;

import storage.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuthService {
    private static final Pattern USER_PATTERN = Pattern.compile(
            "\\{\\s*\"username\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"\\s*,\\s*\"password\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"\\s*\\}"
    );

    public synchronized void initialize() throws IOException {
        Path usersFile = AppPaths.usersJsonFile();
        if (Files.size(usersFile) == 0) {
            Files.writeString(usersFile, "[]\n", StandardCharsets.UTF_8);
        }
    }

    public synchronized boolean registerUser(String user, String plainPassword) throws IOException {
        String normalizedUser = normalizeUser(user);
        String normalizedPassword = normalizePassword(plainPassword);

        List<UserRecord> users = loadUsers();
        for (UserRecord currentUser : users) {
            if (currentUser.username.equals(normalizedUser)) {
                return false;
            }
        }

        users.add(new UserRecord(normalizedUser, normalizedPassword));
        saveUsers(users);
        return true;
    }

    public synchronized boolean authenticate(String user, String plainPassword) throws IOException {
        String normalizedUser = normalizeUser(user);
        String normalizedPassword = normalizePassword(plainPassword);

        for (UserRecord currentUser : loadUsers()) {
            if (currentUser.username.equals(normalizedUser) && currentUser.password.equals(normalizedPassword)) {
                return true;
            }
        }

        return false;
    }

    private List<UserRecord> loadUsers() throws IOException {
        String content = Files.readString(AppPaths.usersJsonFile(), StandardCharsets.UTF_8).trim();
        List<UserRecord> users = new ArrayList<>();

        if (content.isEmpty() || content.equals("[]")) {
            return users;
        }

        Matcher matcher = USER_PATTERN.matcher(content);
        while (matcher.find()) {
            users.add(new UserRecord(
                    unescapeJson(matcher.group(1)),
                    unescapeJson(matcher.group(2))
            ));
        }

        return users;
    }

    private void saveUsers(List<UserRecord> users) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("[\n");

        for (int i = 0; i < users.size(); i++) {
            UserRecord user = users.get(i);
            builder.append("  {\"username\": \"")
                    .append(escapeJson(user.username))
                    .append("\", \"password\": \"")
                    .append(escapeJson(user.password))
                    .append("\"}");

            if (i < users.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }

        builder.append("]\n");
        Files.writeString(AppPaths.usersJsonFile(), builder.toString(), StandardCharsets.UTF_8);
    }

    private String normalizeUser(String user) throws IOException {
        if (user == null || user.trim().isEmpty()) {
            throw new IOException("Usuario nao pode ser vazio.");
        }
        return user.trim();
    }

    private String normalizePassword(String password) throws IOException {
        if (password == null || password.isEmpty()) {
            throw new IOException("Senha nao pode ser vazia.");
        }
        return password;
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String unescapeJson(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static class UserRecord {
        private final String username;
        private final String password;

        private UserRecord(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}
