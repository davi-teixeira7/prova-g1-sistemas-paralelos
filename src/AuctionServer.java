import java.io.*;
import java.net.*;
import java.util.*;

public class AuctionServer {
    private static final int PORT = 8080;
    private static String itemName;
    private static double currentBid = 0.0;
    private static String highestBidder = "Nenhum";
    private static boolean isActive = true;
    private static boolean isPrivado = false;
    private static ServerSocket serverSocket;
    private static final Set<PrintWriter> clientWriters = new HashSet<>();
    private static final Map<String, String> CREDENCIAIS = new HashMap<>();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("--- CONFIGURAÇÃO DO LEILÃO ---");
        System.out.print("Nome do item: ");
        itemName = scanner.nextLine();

        System.out.print("Tipo de leilao (1-Publico | 2-Privado): ");
        String opcao = scanner.nextLine();

        if (opcao.equals("2")) {
            isPrivado = true;
            System.out.print("Quantas pessoas deseja cadastrar? ");
            int qtd = Integer.parseInt(scanner.nextLine());

            for (int i = 1; i <= qtd; i++) {
                System.out.println("\nCadastro " + i + "/" + qtd);
                System.out.print("Usuario: ");
                String u = scanner.nextLine();
                System.out.print("Senha: ");
                String p = scanner.nextLine();
                CREDENCIAIS.put(u, p);
            }
        }

        System.out.println("\n[SISTEMA] Servidor ON em modo " + (isPrivado ? "PRIVADO" : "PUBLICO"));
        System.out.println("Digite 'encerrar' para finalizar o leilao.");

        new Thread(() -> {
            while (isActive) {
                if (scanner.hasNextLine() && scanner.nextLine().equalsIgnoreCase("encerrar")) {
                    encerrarLeilao();
                }
            }
        }).start();

        try {
            serverSocket = new ServerSocket(PORT);
            while (isActive) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            if (isActive) System.out.println("Erro no servidor: " + e.getMessage());
        }
    }

    public static synchronized void processBid(String user, String encodedAmount, PrintWriter writer) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedAmount);
            double amount = Double.parseDouble(new String(decodedBytes));

            if (amount > currentBid) {
                currentBid = amount;
                highestBidder = user;
                broadcast("NOVO_LANCE:" + user + ":" + amount);
                System.out.println("[LANCE] " + user + " ofertou R$ " + amount);
            } else {
                writer.println("SISTEMA: Lance negado! Valor atual: R$ " + currentBid);
            }
        } catch (Exception e) {
            writer.println("SISTEMA: Erro na descriptografia do lance.");
        }
    }

    private static synchronized void broadcast(String message) {
        for (PrintWriter writer : clientWriters) {
            writer.println(message);
        }
    }

    private static void encerrarLeilao() {
        isActive = false;

        System.out.println("\n========================================");
        System.out.println("      LEILÃO FINALIZADO OFICIALMENTE      ");
        System.out.println("========================================");
        System.out.println("VENCEDOR: " + highestBidder);
        System.out.println("VALOR FINAL: R$ " + currentBid);
        System.out.println("========================================\n");

        broadcast("ENCERRADO: Vencedor: " + highestBidder + " | Total: R$ " + currentBid);

        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            System.out.println("Encerrando conexoes...");
        }

        System.exit(0);
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        public ClientHandler(Socket socket) { this.socket = socket; }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String user = "";
                if (isPrivado) {
                    boolean autenticado = false;
                    for (int tentativas = 0; tentativas < 3; tentativas++) {
                        out.println(tentativas == 0 ? "AUTH_REQUIRED" : "AUTH_RETRY");
                        out.println("Login: ");
                        user = in.readLine();
                        out.println("Senha: ");
                        String pass = in.readLine();

                        if (CREDENCIAIS.containsKey(user) && CREDENCIAIS.get(user).equals(pass)) {
                            autenticado = true;
                            break;
                        }
                    }

                    if (!autenticado) {
                        out.println("ACESSO_NEGADO");
                        return;
                    }
                } else {
                    out.println("AUTH_NONE");
                    out.println("Seu Nome: ");
                    user = in.readLine();
                }

                out.println("LOGIN_OK:" + itemName + ":" + currentBid);
                synchronized (clientWriters) { clientWriters.add(out); }

                String msg;
                while ((msg = in.readLine()) != null) {
                    processBid(user, msg, out);
                }
            } catch (IOException e) {
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}