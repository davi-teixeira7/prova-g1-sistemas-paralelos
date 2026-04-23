import auth.AuthService;
import security.CryptoService;
import storage.AppPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class AuctionServer {
    private static final int PRIMARY_TCP_PORT = 8080;
    private static final Map<Integer, Integer> REPLICA_PORTS = Map.of(
            1, 9091,
            2, 9092
    );
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Set<PrintWriter> clientWriters = Collections.synchronizedSet(new HashSet<>());
    private static final List<BidEvent> bidHistory = new ArrayList<>();
    private static final List<ReplicaTarget> replicaTargets = new ArrayList<>();

    private static String itemName;
    private static double currentBid = 0.0;
    private static String highestBidder = "Nenhum";
    private static volatile boolean isActive = true;
    private static ServerSocket serverSocket;
    private static DatagramSocket replicationSocket;
    private static DatagramSocket replicaListenerSocket;
    private static AuthService authService;
    private static ServerRole serverRole = ServerRole.PRIMARY;
    private static int replicaId;

    public static void main(String[] args) {
        if (!configureRole(args)) {
            return;
        }

        if (serverRole == ServerRole.REPLICA) {
            runReplica();
            return;
        }

        runPrimary();
    }

    private static boolean configureRole(String[] args) {
        if (args.length == 0 || "primary".equalsIgnoreCase(args[0])) {
            serverRole = ServerRole.PRIMARY;
            return true;
        }

        if ("replica".equalsIgnoreCase(args[0]) && args.length >= 2) {
            try {
                replicaId = Integer.parseInt(args[1]);
                if (!REPLICA_PORTS.containsKey(replicaId)) {
                    System.out.println("[ERRO] Replica invalida. Use 1 ou 2.");
                    return false;
                }

                serverRole = ServerRole.REPLICA;
                return true;
            } catch (NumberFormatException e) {
                System.out.println("[ERRO] Replica invalida. Use 1 ou 2.");
                return false;
            }
        }

        System.out.println("Uso:");
        System.out.println("  java AuctionServer");
        System.out.println("  java AuctionServer primary");
        System.out.println("  java AuctionServer replica 1");
        System.out.println("  java AuctionServer replica 2");
        return false;
    }

    private static void runPrimary() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("--- CONFIGURACAO DO LEILAO ---");
        System.out.print("Nome do item: ");
        itemName = scanner.nextLine().trim();

        try {
            authService = new AuthService();
            authService.initialize();
            replicationSocket = new DatagramSocket();
            prepareReplicaTargets();
            serverSocket = new ServerSocket(PRIMARY_TCP_PORT);
        } catch (IOException e) {
            System.out.println("[ERRO] Nao foi possivel inicializar o servidor primario: " + e.getMessage());
            return;
        }

        BidEvent openingEvent = registerAuctionEvent(
                "SISTEMA",
                formatAmount(currentBid),
                "ABERTURA",
                "Leilao iniciado para o produto " + itemName + " com lance inicial de R$ " + formatAmount(currentBid) + "."
        );
        replicateEvent(openingEvent);

        System.out.println("\n[SISTEMA] Servidor primario ON em TCP " + PRIMARY_TCP_PORT + ".");
        System.out.println("[SISTEMA] Replicacao UDP ativa para replicas locais nas portas 9091 e 9092.");
        System.out.println("[SISTEMA] Todo leilao e publico para usuarios autenticados.");
        System.out.println("Digite 'encerrar' para finalizar o leilao.");

        new Thread(() -> {
            while (isActive) {
                if (scanner.hasNextLine() && scanner.nextLine().equalsIgnoreCase("encerrar")) {
                    encerrarLeilao();
                }
            }
        }).start();

        try {
            while (isActive) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            if (isActive) {
                System.out.println("Erro no servidor: " + e.getMessage());
            }
        }
    }

    private static void runReplica() {
        int replicaPort = REPLICA_PORTS.get(replicaId);
        System.out.println("[REPLICA " + replicaId + "] Ouvindo replicacao UDP na porta " + replicaPort + ".");

        try {
            replicaListenerSocket = new DatagramSocket(replicaPort);
            while (isActive) {
                DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
                replicaListenerSocket.receive(packet);

                String encryptedPayload = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                String payload;
                try {
                    payload = CryptoService.decrypt(encryptedPayload);
                } catch (IllegalStateException e) {
                    System.out.println("[REPLICA " + replicaId + "] Pacote invalido ignorado.");
                    continue;
                }

                applyReplicationPayload(payload);
            }
        } catch (SocketException e) {
            if (isActive) {
                System.out.println("[REPLICA " + replicaId + "] Erro ao abrir porta UDP: " + e.getMessage());
            }
        } catch (IOException e) {
            if (isActive) {
                System.out.println("[REPLICA " + replicaId + "] Erro ao receber atualizacao: " + e.getMessage());
            }
        }
    }

    public static synchronized void processBid(String user, String rawAmount, PrintWriter writer) {
        if (!isActive) {
            sendSecure(writer, "SISTEMA: O leilao ja foi encerrado.");
            return;
        }

        String normalizedAmount = rawAmount == null ? "" : rawAmount.trim().replace(',', '.');

        try {
            double amount = Double.parseDouble(normalizedAmount);
            if (amount > currentBid) {
                currentBid = amount;
                highestBidder = user;

                BidEvent acceptedEvent = registerAuctionEvent(
                        user,
                        formatAmount(amount),
                        "ACEITO",
                        user + " fez um lance no produto " + itemName + " por R$ " + formatAmount(amount) + "."
                );
                replicateEvent(acceptedEvent);

                broadcast("NOVO_LANCE:" + user + ":" + formatAmount(amount));
                System.out.println("[LANCE] " + user + " ofertou R$ " + formatAmount(amount));
            } else {
                BidEvent deniedEvent = registerAuctionEvent(
                        user,
                        formatAmount(amount),
                        "NEGADO",
                        user + " tentou ofertar o produto " + itemName + " por R$ " + formatAmount(amount)
                                + ", mas o lance atual e R$ " + formatAmount(currentBid) + "."
                );
                replicateEvent(deniedEvent);

                sendSecure(writer, "SISTEMA: Lance negado! Valor atual: R$ " + formatAmount(currentBid));
            }
        } catch (NumberFormatException e) {
            BidEvent invalidEvent = registerAuctionEvent(
                    user,
                    normalizedAmount.isEmpty() ? "-" : normalizedAmount,
                    "INVALIDO",
                    user + " enviou um lance invalido para o produto " + itemName + ": " + normalizedAmount + "."
            );
            replicateEvent(invalidEvent);

            sendSecure(writer, "SISTEMA: Lance invalido. Informe apenas numeros.");
        }
    }

    private static void broadcast(String message) {
        synchronized (clientWriters) {
            for (PrintWriter writer : clientWriters) {
                sendSecure(writer, message);
            }
        }
    }

    private static synchronized void encerrarLeilao() {
        if (!isActive) {
            return;
        }

        isActive = false;
        BidEvent closingEvent = registerAuctionEvent(
                "SISTEMA",
                formatAmount(currentBid),
                "ENCERRADO",
                "Leilao do produto " + itemName + " encerrado. Vencedor: " + highestBidder
                        + ". Valor final: R$ " + formatAmount(currentBid) + "."
        );
        replicateEvent(closingEvent);

        System.out.println("\n========================================");
        System.out.println("      LEILAO FINALIZADO OFICIALMENTE      ");
        System.out.println("========================================");
        System.out.println("VENCEDOR: " + highestBidder);
        System.out.println("VALOR FINAL: R$ " + formatAmount(currentBid));
        System.out.println("========================================\n");

        broadcast("ENCERRADO: Vencedor: " + highestBidder + " | Produto: " + itemName + " | Total: R$ " + formatAmount(currentBid));
        persistAuctionHistory();

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            if (replicationSocket != null && !replicationSocket.isClosed()) {
                replicationSocket.close();
            }
        } catch (IOException e) {
            System.out.println("Encerrando conexoes...");
        }

        System.exit(0);
    }

    private static synchronized BidEvent registerAuctionEvent(String user, String amount, String status, String description) {
        BidEvent event = new BidEvent(
                LocalDateTime.now(),
                itemName,
                user,
                amount,
                status,
                description
        );
        bidHistory.add(event);
        return event;
    }

    private static synchronized void applyReplicationPayload(String payload) {
        String[] parts = payload.split("\\|", 11);
        if (parts.length < 11 || !"REPL".equals(parts[0])) {
            System.out.println("[REPLICA " + replicaId + "] Pacote ignorado.");
            return;
        }

        String replicationType = unescapeReplicaField(parts[1]);
        String status = unescapeReplicaField(parts[2]);
        String user = unescapeReplicaField(parts[3]);
        String product = unescapeReplicaField(parts[4]);
        String amount = unescapeReplicaField(parts[5]);
        LocalDateTime timestamp = LocalDateTime.parse(parts[6], TIMESTAMP_FORMATTER);
        highestBidder = unescapeReplicaField(parts[7]);
        currentBid = Double.parseDouble(parts[8]);
        boolean remoteActive = Boolean.parseBoolean(parts[9]);
        String description = unescapeReplicaField(parts[10]);

        itemName = product;
        bidHistory.add(new BidEvent(timestamp, product, user, amount, status, description));

        System.out.println("[REPLICA " + replicaId + "] " + replicationType
                + " | produto=" + product
                + " | status=" + status
                + " | usuario=" + user
                + " | maior lance=R$ " + formatAmount(currentBid)
                + " | lider=" + highestBidder);

        if (!remoteActive || "CLOSE".equals(replicationType)) {
            isActive = false;
            System.out.println("[REPLICA " + replicaId + "] Leilao encerrado e estado final sincronizado.");
            if (replicaListenerSocket != null && !replicaListenerSocket.isClosed()) {
                replicaListenerSocket.close();
            }
        }
    }

    private static void prepareReplicaTargets() throws UnknownHostException {
        replicaTargets.clear();
        InetAddress localhost = InetAddress.getByName("127.0.0.1");
        for (Map.Entry<Integer, Integer> entry : REPLICA_PORTS.entrySet()) {
            replicaTargets.add(new ReplicaTarget(entry.getKey(), localhost, entry.getValue()));
        }
    }

    private static synchronized void replicateEvent(BidEvent event) {
        if (serverRole != ServerRole.PRIMARY || replicationSocket == null || replicationSocket.isClosed()) {
            return;
        }

        String encryptedPayload = CryptoService.encrypt(event.toReplicationPayload(currentBid, highestBidder, isActive));
        byte[] bytes = encryptedPayload.getBytes(StandardCharsets.UTF_8);

        for (ReplicaTarget target : replicaTargets) {
            try {
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, target.address, target.port);
                replicationSocket.send(packet);
            } catch (IOException e) {
                System.out.println("[ERRO] Falha ao replicar para replica " + target.id + ": " + e.getMessage());
            }
        }
    }

    private static synchronized void persistAuctionHistory() {
        try {
            Path logFile = AppPaths.auctionLogFile();

            List<String> lines = new ArrayList<>();
            lines.add("===== LEILAO: " + itemName + " | " + LocalDateTime.now().format(TIMESTAMP_FORMATTER) + " =====");

            for (BidEvent event : bidHistory) {
                lines.add(event.toLogLine());
            }

            lines.add("===== FIM DO LEILAO =====");
            lines.add("");

            Files.write(
                    logFile,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.out.println("[ERRO] Nao foi possivel salvar o historico em log: " + e.getMessage());
        }
    }

    private static String formatAmount(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private static void sendSecure(PrintWriter out, String message) {
        out.println(CryptoService.encrypt(message));
    }

    private static String readSecure(BufferedReader in) throws IOException {
        String encryptedMessage = in.readLine();
        if (encryptedMessage == null) {
            return null;
        }

        try {
            return CryptoService.decrypt(encryptedMessage);
        } catch (IllegalStateException e) {
            throw new IOException("Falha ao ler mensagem segura.", e);
        }
    }

    private static String escapeReplicaField(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("|", "\\p");
    }

    private static String unescapeReplicaField(String value) {
        return value
                .replace("\\p", "|")
                .replace("\\\\", "\\");
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;

        private ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            PrintWriter out = null;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter currentOut = new PrintWriter(socket.getOutputStream(), true)) {
                out = currentOut;

                String user = authenticateUser(in, out);
                if (user == null) {
                    return;
                }

                sendSecure(out, "LOGIN_OK:" + itemName + ":" + formatAmount(currentBid));
                clientWriters.add(out);

                String message;
                while (isActive && (message = readSecure(in)) != null) {
                    processBid(user, message, out);
                }
            } catch (IOException ignored) {
            } finally {
                if (out != null) {
                    clientWriters.remove(out);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private String authenticateUser(BufferedReader in, PrintWriter out) throws IOException {
            while (true) {
                sendSecure(out, "AUTH_MENU");
                sendSecure(out, "Escolha uma opcao (1-Entrar | 2-Cadastrar): ");

                String option = readSecure(in);
                if (option == null) {
                    return null;
                }

                option = option.trim();
                if (option.equals("1")) {
                    return attemptLogin(in, out);
                }
                if (option.equals("2")) {
                    String registeredUser = registerUser(in, out);
                    if (registeredUser == null) {
                        return null;
                    }
                    if (!registeredUser.isEmpty()) {
                        return registeredUser;
                    }
                    continue;
                }

                sendSecure(out, "SISTEMA: Opcao invalida. Escolha 1 para entrar ou 2 para cadastrar.");
            }
        }

        private String attemptLogin(BufferedReader in, PrintWriter out) throws IOException {
            for (int tentativas = 0; tentativas < 3; tentativas++) {
                sendSecure(out, tentativas == 0 ? "AUTH_REQUIRED" : "AUTH_RETRY");
                sendSecure(out, "Login: ");
                String informedUser = readSecure(in);
                if (informedUser == null) {
                    return null;
                }

                sendSecure(out, "Senha: ");
                String informedPassword = readSecure(in);
                if (informedPassword == null) {
                    return null;
                }

                try {
                    if (authService != null && authService.authenticate(informedUser.trim(), informedPassword)) {
                        return informedUser.trim();
                    }
                } catch (IOException e) {
                    System.out.println("[ERRO] Falha ao autenticar usuario " + informedUser + ": " + e.getMessage());
                    sendSecure(out, "ACESSO_NEGADO");
                    return null;
                }
            }

            sendSecure(out, "ACESSO_NEGADO");
            return null;
        }

        private String registerUser(BufferedReader in, PrintWriter out) throws IOException {
            sendSecure(out, "REGISTER_REQUIRED");
            sendSecure(out, "Novo usuario: ");
            String informedUser = readSecure(in);
            if (informedUser == null) {
                return null;
            }

            sendSecure(out, "Senha: ");
            String informedPassword = readSecure(in);
            if (informedPassword == null) {
                return null;
            }

            try {
                if (authService != null && authService.registerUser(informedUser.trim(), informedPassword)) {
                    sendSecure(out, "REGISTER_OK");
                    return informedUser.trim();
                }
                sendSecure(out, "REGISTER_FAIL: Usuario ja existe. Tente entrar com essa conta.");
                return "";
            } catch (IOException e) {
                System.out.println("[ERRO] Falha ao cadastrar usuario " + informedUser + ": " + e.getMessage());
                sendSecure(out, "REGISTER_FAIL: Nao foi possivel criar a conta.");
                return "";
            }
        }
    }

    private static class BidEvent {
        private final LocalDateTime timestamp;
        private final String product;
        private final String user;
        private final String amount;
        private final String status;
        private final String description;

        private BidEvent(LocalDateTime timestamp, String product, String user, String amount, String status, String description) {
            this.timestamp = timestamp;
            this.product = product;
            this.user = user;
            this.amount = amount;
            this.status = status;
            this.description = description;
        }

        private String toLogLine() {
            return "[" + timestamp.format(TIMESTAMP_FORMATTER) + "] "
                    + "status=" + status
                    + " | usuario=" + user
                    + " | produto=" + product
                    + " | valor=" + amount
                    + " | descricao=" + description;
        }

        private String toReplicationPayload(double replicatedCurrentBid, String replicatedHighestBidder, boolean active) {
            return "REPL|"
                    + escapeReplicaField(replicationType()) + "|"
                    + escapeReplicaField(status) + "|"
                    + escapeReplicaField(user) + "|"
                    + escapeReplicaField(product) + "|"
                    + escapeReplicaField(amount) + "|"
                    + timestamp.format(TIMESTAMP_FORMATTER) + "|"
                    + escapeReplicaField(replicatedHighestBidder) + "|"
                    + formatAmount(replicatedCurrentBid) + "|"
                    + active + "|"
                    + escapeReplicaField(description);
        }

        private String replicationType() {
            if ("ABERTURA".equals(status)) {
                return "OPEN";
            }
            if ("ENCERRADO".equals(status)) {
                return "CLOSE";
            }
            return "BID";
        }
    }

    private static class ReplicaTarget {
        private final int id;
        private final InetAddress address;
        private final int port;

        private ReplicaTarget(int id, InetAddress address, int port) {
            this.id = id;
            this.address = address;
            this.port = port;
        }
    }

    private enum ServerRole {
        PRIMARY,
        REPLICA
    }
}
