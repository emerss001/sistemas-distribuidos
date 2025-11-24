// ServidorMestre.java
import java.io.IOException;
import java.net.InetAddress;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ServidorMestre {

    static class Player {
        InetAddress ip;
        int port;
        String name;
        Player(InetAddress ip, int port, String name) { this.ip = ip; this.port = port; this.name = name; }
    }

    private static List<Player> players = new ArrayList<>();
    private static Connection conn;
    // Tornamos a palavra estática para ser acessada no método de perguntas
    private static String secretWord = "";
    private static String secretWordClean = "";

    public static void main(String[] args) {
        try {
            conn = new Connection(9876);
            Scanner console = new Scanner(System.in);

            System.out.println("### SERVIDOR MESTRE ###");
            System.out.print("Mestre, digite a palavra secreta: ");
            secretWord = console.nextLine();
            secretWordClean = removerAcentos(secretWord);

            System.out.println("Aguardando 2 jogadores...");
            while (players.size() < 2) {
                String[] data = conn.receive().split("\\|"); // msg|ip|port
                Player p = new Player(InetAddress.getByName(data[1]), Integer.parseInt(data[2]), data[0]);
                players.add(p);
                System.out.println(p.name + " entrou.");
            }

            System.out.println("Jogo iniciado!");
            broadcast("MSG:O jogo começou! A palavra tem " + secretWord.length() + " letras.");

            int turnIndex = 0;
            boolean gameRunning = true;

            while (gameRunning) {
                Player atual = players.get(turnIndex);

                broadcastExcept("MSG:Vez do jogador " + atual.name, atual);

                boolean turnEnded = false;
                while (!turnEnded) {
                    // Envia Menu para o jogador e pede INPUT
                    String menu = "\n--- SUA VEZ ---\n1. Regras\n2. Perguntar\n3. Chutar\n4. Passar\nEscolha: ";
                    conn.sendTo("INPUT:" + menu, atual.ip, atual.port);

                    String respData = conn.receive().split("\\|")[0];

                    switch (respData) {
                        case "1": // Regras
                            conn.sendTo("MSG:Regras: Faça perguntas de Sim/Não ou tente chutar.", atual.ip, atual.port);
                            break;
                        case "2": // Perguntar
                            conn.sendTo("INPUT:Digite sua pergunta: ", atual.ip, atual.port);
                            String pergunta = conn.receive().split("\\|")[0];

                            broadcast("MSG:" + atual.name + " perguntou: " + pergunta);

                            // AQUI CHAMAMOS O NOVO MENU
                            String respostaMestre = perguntarAoMestre(console, pergunta, atual.name);

                            broadcast("MSG:Mestre respondeu: " + respostaMestre);

                            conn.sendTo("INPUT:Deseja chutar agora? (S/N): ", atual.ip, atual.port);
                            if (conn.receive().split("\\|")[0].equalsIgnoreCase("S")) {
                                turnEnded = processarChute(atual, console);
                                if (!turnEnded && gameRunning) turnEnded = true;
                            } else {
                                turnEnded = true;
                            }
                            break;
                        case "3": // Chutar
                            turnEnded = processarChute(atual, console);
                            break;
                        case "4": // Passar
                            broadcast("MSG:" + atual.name + " passou a vez.");
                            turnEnded = true;
                            break;
                        default:
                            conn.sendTo("MSG:Opção inválida.", atual.ip, atual.port);
                    }

                    if (secretWordClean.equals("ACERTOU_FIM")) {
                        gameRunning = false;
                        turnEnded = true;
                    }
                }
                turnIndex = (turnIndex + 1) % players.size();
            }

            broadcast("FIM");
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean processarChute(Player player, Scanner console) throws IOException {
        conn.sendTo("INPUT:Digite seu chute: ", player.ip, player.port);
        String chute = conn.receive().split("\\|")[0];

        if (removerAcentos(chute).equalsIgnoreCase(secretWordClean)) {
            broadcast("MSG:O jogador " + player.name + " ACERTOU! A palavra era " + secretWord);
            secretWordClean = "ACERTOU_FIM";
            return true;
        } else {
            broadcast("MSG:" + player.name + " chutou '" + chute + "' e ERROU.");

            // Menu de Dicas ao errar
            String dica = "";
            while(true) {
                System.out.println("\nO jogador "+ player.name + " errou. Escolha a dica:");
                System.out.println("1. Frio");
                System.out.println("2. Morno");
                System.out.println("3. Quente");
                System.out.println("4. Quentíssimo");
                System.out.println("5. Relembrar palavra");
                System.out.print("Opção: ");
                String op = console.nextLine();

                if(op.equals("5")) {
                    System.out.println(">> Palavra secreta: " + secretWord);
                    continue;
                }
                if(op.equals("1")) { dica = "Frio"; break; }
                if(op.equals("2")) { dica = "Morno"; break; }
                if(op.equals("3")) { dica = "Quente"; break; }
                if(op.equals("4")) { dica = "Quentíssimo"; break; }
            }

            broadcast("MSG:Dica do Mestre: " + dica);
            return true;
        }
    }

    // --- MÉTODO ATUALIZADO COM O NOVO MENU ---
    private static String perguntarAoMestre(Scanner console, String pergunta, String nomeJogador) {
        System.out.println("\n>>> NOVA PERGUNTA DE " + nomeJogador.toUpperCase() + ": " + pergunta);

        while (true) {
            System.out.println("\nMenu do jogo - perfil Mestre:");
            System.out.println("1. Sim.");
            System.out.println("2. Não.");
            System.out.println("3. Talvez.");
            System.out.println("4. Não sei.");
            System.out.println("5. Não posso responder.");
            System.out.println("6. Relembrar qual palavra escolhi.");
            System.out.print("Sua escolha: ");

            String choice = console.nextLine();

            if (choice.equals("6")) {
                System.out.println("\n>> A PALAVRA SECRETA É: " + secretWord);
                // O loop continua, pois o mestre ainda não deu a resposta para o jogador
                continue;
            } else if (choice.equals("1")) {
                return "Sim.";
            } else if (choice.equals("2")) {
                return "Não.";
            } else if (choice.equals("3")) {
                return "Talvez.";
            } else if (choice.equals("4")) {
                return "Não sei.";
            } else if (choice.equals("5")) {
                return "Não posso responder.";
            } else {
                System.out.println("Opção inválida. Tente novamente.");
            }
        }
    }

    private static void broadcast(String msg) throws IOException {
        for (Player p : players) conn.sendTo(msg, p.ip, p.port);
    }

    private static void broadcastExcept(String msg, Player except) throws IOException {
        for (Player p : players) {
            if (!p.name.equals(except.name)) conn.sendTo(msg, p.ip, p.port);
        }
    }

    public static String removerAcentos(String str) {
        return Normalizer.normalize(str, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "").replace(" ", "").toLowerCase();
    }
}