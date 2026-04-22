import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.Scanner;

public class AuctionClient {
    public static void main(String[] args) {
        try (Socket socket = new Socket("127.0.0.1", 8080)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            String statusInfo = "";

            while (true) {
                String serverCommand = in.readLine();
                if (serverCommand == null) return;

                if (serverCommand.equals("AUTH_REQUIRED") || serverCommand.equals("AUTH_RETRY")) {
                    if (serverCommand.equals("AUTH_RETRY")) {
                        System.out.println("\n[ERRO] Credenciais invalidas. Tente novamente.");
                    } else {
                        System.out.println("[MODO PRIVADO]");
                    }
                    System.out.print(in.readLine());
                    out.println(scanner.nextLine());
                    System.out.print(in.readLine());
                    out.println(scanner.nextLine());
                } else if (serverCommand.equals("AUTH_NONE")) {
                    System.out.println("[MODO PUBLICO]");
                    System.out.print(in.readLine());
                    out.println(scanner.nextLine());
                } else if (serverCommand.equals("ACESSO_NEGADO")) {
                    System.out.println("\n[ERRO] Acesso negado. Limite de tentativas excedido.");
                    return;
                } else if (serverCommand.startsWith("LOGIN_OK")) {
                    statusInfo = serverCommand;
                    break;
                }
            }

            String[] info = statusInfo.split(":");
            System.out.println("\n========================================");
            System.out.println("Bem-vindo ao leilao de: " + info[1].toUpperCase());
            System.out.println("Lance atual: R$ " + info[2]);
            System.out.println("========================================\n");

            Thread t = new Thread(() -> {
                try {
                    String s;
                    while ((s = in.readLine()) != null) {
                        System.out.print("\r" + s + "\n");

                        if (s.startsWith("ENCERRADO")) {
                            System.out.println("\n[SISTEMA] O leilao chegou ao fim. Ate a proxima!");
                            System.exit(0);
                        }

                        System.out.print("Seu lance: ");
                    }
                } catch (IOException ignored) {}
            });
            t.setDaemon(true);
            t.start();

            System.out.print("Seu lance: ");
            while (true) {
                if (scanner.hasNextLine()) {
                    String lance = scanner.nextLine();
                    if (lance.isEmpty()) continue;

                    String encoded = Base64.getEncoder().encodeToString(lance.getBytes());
                    out.println(encoded);
                }
            }

        } catch (IOException e) {
            System.out.println("\n[ERRO] Nao foi possivel conectar. O servidor esta online?");
        }
    }
}