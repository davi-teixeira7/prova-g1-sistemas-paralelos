import security.CryptoService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class AuctionClient {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            String statusInfo = "";

            while (true) {
                String serverCommand = readSecure(in);
                if (serverCommand == null) {
                    return;
                }

                if (serverCommand.equals("AUTH_MENU")) {
                    System.out.println("[AUTENTICACAO]");
                    System.out.print(readSecure(in));

                    String option;
                    do {
                        option = scanner.nextLine().trim();
                    } while (!option.equals("1") && !option.equals("2"));

                    sendSecure(out, option);
                } else if (serverCommand.equals("AUTH_REQUIRED") || serverCommand.equals("AUTH_RETRY")) {
                    if (serverCommand.equals("AUTH_RETRY")) {
                        System.out.println("\n[ERRO] Credenciais invalidas. Tente novamente.");
                    } else {
                        System.out.println("[LOGIN]");
                    }

                    System.out.print(readSecure(in));
                    String username = scanner.nextLine();
                    sendSecure(out, username);

                    System.out.print(readSecure(in));
                    String password = scanner.nextLine();
                    sendSecure(out, password);
                } else if (serverCommand.equals("REGISTER_REQUIRED")) {
                    System.out.println("[CADASTRO]");
                    System.out.print(readSecure(in));
                    String username = scanner.nextLine();
                    sendSecure(out, username);

                    System.out.print(readSecure(in));
                    String password = scanner.nextLine();
                    sendSecure(out, password);
                } else if (serverCommand.equals("REGISTER_OK")) {
                    System.out.println("\n[SISTEMA] Conta criada com sucesso. Entrando no leilao...");
                } else if (serverCommand.startsWith("REGISTER_FAIL")) {
                    System.out.println("\n[ERRO] " + serverCommand.substring("REGISTER_FAIL:".length()).trim());
                } else if (serverCommand.equals("ACESSO_NEGADO")) {
                    System.out.println("\n[ERRO] Acesso negado. Limite de tentativas excedido.");
                    return;
                } else if (serverCommand.startsWith("SISTEMA:")) {
                    System.out.println("\n" + serverCommand);
                } else if (serverCommand.startsWith("LOGIN_OK")) {
                    statusInfo = serverCommand;
                    break;
                }
            }

            String[] info = statusInfo.split(":", 3);
            System.out.println("\n========================================");
            System.out.println("Bem-vindo ao leilao de: " + info[1].toUpperCase());
            System.out.println("Lance atual: R$ " + info[2]);
            System.out.println("========================================\n");

            Thread listenerThread = new Thread(() -> {
                try {
                    String message;
                    while ((message = readSecure(in)) != null) {
                        System.out.print("\r" + message + "\n");

                        if (message.startsWith("ENCERRADO")) {
                            System.out.println("\n[SISTEMA] O leilao chegou ao fim. Ate a proxima!");
                            System.exit(0);
                        }

                        System.out.print("Seu lance: ");
                    }
                } catch (IOException ignored) {
                }
            });
            listenerThread.setDaemon(true);
            listenerThread.start();

            System.out.print("Seu lance: ");
            while (true) {
                if (scanner.hasNextLine()) {
                    String lance = scanner.nextLine().trim();
                    if (lance.isEmpty()) {
                        continue;
                    }

                    sendSecure(out, lance.replace(',', '.'));
                }
            }
        } catch (IOException e) {
            System.out.println("\n[ERRO] Nao foi possivel conectar. O servidor esta online?");
        }
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
}
