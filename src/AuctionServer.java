import java.io.*;
import java.net.*;
import java.util.*;

public class AuctionServer {
    private static final int PORT = 8080;

    // Estado do Leilão
    private static String itemName;
    private static double currentBid = 0.0;
    private static String highestBidder = "Nenhum";
    private static boolean isActive = true;

    // Gerenciamento de Clientes e Histórico
    private static Set<PrintWriter> clientWriters = new HashSet<>();
    private static List<String> history = new ArrayList<>();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // a) Cadastrar item de leilão
        System.out.print("Digite o nome do item a ser leiloado: ");
        itemName = scanner.nextLine();
        System.out.println("Leilão iniciado para: " + itemName);
        System.out.println("Para encerrar o leilão, digite 'encerrar' a qualquer momento.\n");

        // Thread para escutar o comando de encerrar o leilão do Administrador
        new Thread(() -> {
            while (isActive) {
                if (scanner.nextLine().equalsIgnoreCase("encerrar")) {
                    encerrarLeilao();
                }
            }
        }).start();

        // Inicia o Servidor TCP
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor aguardando conexões na porta " + PORT + "...");

            while (isActive) {
                Socket clientSocket = serverSocket.accept();
                // Gerenciamento de threads para conexões concorrentes
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            if (isActive) e.printStackTrace();
        }
    }

    // b) Receber e armazenar lances (Thread-safe)
    public static synchronized void processBid(String user, double amount, PrintWriter writer) {
        if (!isActive) {
            writer.println("ERRO: O leilão já foi encerrado.");
            return;
        }

        // Validar lances
        if (amount > currentBid) {
            currentBid = amount;
            highestBidder = user;

            String log = "Lance aceito! " + user + " ofereceu R$ " + amount;
            history.add(log);
            System.out.println(log);

            // i. Notificar todos os participantes sobre novos lances
            broadcast("NOVO_LANCE:" + user + ":" + amount);
        } else {
            writer.println("ERRO: O lance deve ser maior que o atual (R$ " + currentBid + ").");
        }
    }

    private static synchronized void broadcast(String message) {
        for (PrintWriter writer : clientWriters) {
            writer.println(message);
        }
    }

    // c) Encerrar o leilão do item
    private static synchronized void encerrarLeilao() {
        isActive = false;
        String resultado = "LEILAO_ENCERRADO: Vencedor: " + highestBidder + " | Valor pago: R$ " + currentBid;
        System.out.println(resultado);

        // i. Notificar vencedores e conectados
        broadcast(resultado);

        // Persistência: Salvar histórico em arquivo
        salvarHistorico();
        System.exit(0);
    }

    private static void salvarHistorico() {
        try (PrintWriter fileWriter = new PrintWriter(new FileWriter("historico_leilao.txt"))) {
            fileWriter.println("=== HISTÓRICO DO LEILÃO ===");
            fileWriter.println("Item: " + itemName);
            fileWriter.println("Vencedor: " + highestBidder + " (R$ " + currentBid + ")");
            fileWriter.println("---------------------------");
            for (String log : history) {
                fileWriter.println(log);
            }
            System.out.println("Histórico salvo no arquivo 'historico_leilao.txt'.");
        } catch (IOException e) {
            System.err.println("Erro ao salvar histórico: " + e.getMessage());
        }
    }

    // Classe interna para lidar com cada cliente em uma Thread separada
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Adiciona o escritor à lista de broadcast
                synchronized (clientWriters) {
                    clientWriters.add(out);
                }

                // Autenticação básica (Bônus Segurança)
                out.println("Bem-vindo ao Leilão de: " + itemName);
                out.println("Lance atual: R$ " + currentBid);
                out.println("Digite seu nome de usuário para entrar: ");
                username = in.readLine();

                System.out.println(username + " entrou no leilão.");
                broadcast("SISTEMA: " + username + " entrou no leilão.");

                String message;
                while ((message = in.readLine()) != null && isActive) {
                    try {
                        double lance = Double.parseDouble(message);
                        processBid(username, lance, out);
                    } catch (NumberFormatException e) {
                        out.println("ERRO: Formato de lance inválido. Digite apenas números.");
                    }
                }
            } catch (IOException e) {
                System.out.println("Cliente " + username + " desconectou.");
            } finally {
                if (username != null) {
                    synchronized (clientWriters) {
                        clientWriters.remove(out);
                    }
                    broadcast("SISTEMA: " + username + " saiu do leilão.");
                }
                try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }
    }
}