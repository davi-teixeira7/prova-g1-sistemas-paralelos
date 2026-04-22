import java.io.*;
import java.net.*;
import java.util.*;

public class AuctionServer {
    private static final int PORT = 8080;
    private static String itemName;
    private static double currentBid = 0.0;
    private static String highestBidder = "Nenhum";
    private static boolean isActive = true;
    private static Set<PrintWriter> clientWriters = new HashSet<>();
    private static List<String> history = new ArrayList<>();

    private static final Map<String, String> CREDENCIAIS = Map.of(
            "nicole", "123",
            "davi", "g1",
            "admin", "admin"
    );

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Item: ");
        itemName = scanner.nextLine();

        new Thread(() -> {
            while (isActive) {
                if (scanner.hasNextLine() && scanner.nextLine().equalsIgnoreCase("encerrar")) {
                    encerrarLeilao();
                }
            }
        }).start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (isActive) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            if (isActive) e.printStackTrace();
        }
    }

    public static synchronized void processBid(String user, String encodedAmount, PrintWriter writer) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedAmount);
            double amount = Double.parseDouble(new String(decodedBytes));

            if (amount > currentBid) {
                currentBid = amount;
                highestBidder = user;
                String log = "Lance: " + user + " - R$ " + amount;
                history.add(log);
                broadcast("NOVO_LANCE:" + user + ":" + amount);
            } else {
                writer.println("ERRO: Lance baixo.");
            }
        } catch (Exception e) {
            writer.println("ERRO: Formato invalido.");
        }
    }

    private static synchronized void broadcast(String message) {
        for (PrintWriter writer : clientWriters) {
            writer.println(message);
        }
    }

    private static void encerrarLeilao() {
        isActive = false;
        broadcast("ENCERRADO:" + highestBidder + ":" + currentBid);
        salvar();
        System.exit(0);
    }

    private static void salvar() {
        try (PrintWriter pw = new PrintWriter(new FileWriter("historico.txt"))) {
            pw.println("Item: " + itemName + " | Vencedor: " + highestBidder);
            for (String h : history) pw.println(h);
        } catch (IOException ignored) {}
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) { this.socket = socket; }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("Login: ");
                String user = in.readLine();
                out.println("Senha: ");
                String pass = in.readLine();

                if (!CREDENCIAIS.containsKey(user) || !CREDENCIAIS.get(user).equals(pass)) {
                    out.println("ACESSO_NEGADO");
                    socket.close();
                    return;
                }

                synchronized (clientWriters) { clientWriters.add(out); }
                out.println("ACESSO_OK:" + itemName + ":" + currentBid);

                String msg;
                while ((msg = in.readLine()) != null) {
                    processBid(user, msg, out);
                }
            } catch (IOException e) {
            } finally {
                synchronized (clientWriters) { clientWriters.remove(out); }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}