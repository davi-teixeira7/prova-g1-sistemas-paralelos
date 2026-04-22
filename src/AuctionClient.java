import java.io.*;
import java.net.*;
import java.util.Scanner;

public class AuctionClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT)) {

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            // Thread separada para escutar mensagens do servidor (Painel de monitoramento)
            Thread listenerThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        // Tratar as mensagens específicas do servidor
                        if (serverMessage.startsWith("NOVO_LANCE:")) {
                            String[] parts = serverMessage.split(":");
                            System.out.println("\n[PAINEL] Novo lance mais alto! " + parts[1] + " ofereceu R$ " + parts[2]);
                            System.out.print("Seu próximo lance: ");
                        }
                        else if (serverMessage.startsWith("LEILAO_ENCERRADO:")) {
                            System.out.println("\n*** O LEILÃO TERMINOU! ***");
                            System.out.println(serverMessage.substring(17));
                            System.exit(0);
                        }
                        else {
                            System.out.println("\n" + serverMessage);
                            System.out.print("Seu lance: ");
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Conexão com o servidor perdida.");
                    System.exit(0);
                }
            });
            listenerThread.start();

            // Interface para enviar lances (Thread principal)
            while (true) {
                String input = scanner.nextLine();
                out.println(input);
            }

        } catch (IOException e) {
            System.out.println("Não foi possível conectar ao servidor. Verifique se ele está rodando.");
        }
    }
}