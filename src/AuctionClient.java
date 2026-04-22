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

            in.readLine();
            System.out.print("Digite seu usuario: ");
            out.println(scanner.nextLine());

            in.readLine();
            System.out.print("Digite sua senha: ");
            out.println(scanner.nextLine());

            String authResponse = in.readLine();
            if (authResponse == null || authResponse.equals("ACESSO_NEGADO")) {
                System.out.println("\n[ERRO] Usuario ou senha invalidos.");
                return;
            }

            System.out.println("\n" + authResponse);
            System.out.println("Sua conexao esta criptografada com Base64.");
            System.out.print("Seu lance: ");

            Thread listener = new Thread(() -> {
                try {
                    String s;
                    while ((s = in.readLine()) != null) {
                        System.out.print("\r" + s + "\nSeu lance: ");
                    }
                } catch (IOException ignored) {}
            });
            listener.setDaemon(true);
            listener.start();

            while (true) {
                if (scanner.hasNextLine()) {
                    String lance = scanner.nextLine();
                    if (lance.isEmpty()) continue;
                    String encoded = Base64.getEncoder().encodeToString(lance.getBytes());
                    out.println(encoded);
                }
            }
        } catch (IOException e) {
            System.out.println("O servidor nao esta respondendo.");
        }
    }
}