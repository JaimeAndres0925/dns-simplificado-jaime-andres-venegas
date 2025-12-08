package src.main.java;
import src.main.java.Registro;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Servidor {
    private static final Map<String, List<Registro>> diccionario = new HashMap<>();

    public static void main(String[] args) {
        String fichero = "dominios.txt";
        int puerto = 5000;

        cargarDiccionario(fichero);

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("Servidor escuchando en puerto " + puerto);
            while (true) {
                try (Socket socket = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                    System.out.println("Cliente conectado: ");
                    out.println("220 Servidor LOOKUP. Comandos: LOOKUP <tipo> <dominio> | EXIT");

                    String raw;
                    while ((raw = in.readLine()) != null) {
                        String linea = cleanLine(raw);
                        if (linea.isEmpty()) continue;

                        try {
                            if (linea.equalsIgnoreCase("EXIT")) {
                                out.println("221 Bye!");
                                break;
                            }
                                 else if (linea.equalsIgnoreCase("LIST")) {
                                    out.println("150 Inicio listado");
                                    for (Map.Entry<String, List<Registro>> entry : diccionario.entrySet()) {
                                        String dominio = entry.getKey();
                                        for (Registro r : entry.getValue()) {
                                            out.println(dominio + " " + r.getTipo() + " " + r.getValor());
                                        }
                                    }
                                    out.println("226 Fin listado");
                                } else if (linea.toUpperCase().startsWith("LOOKUP")) {
                                String[] partes = linea.split("\\s+");
                                if (partes.length == 3) {
                                    String tipo = partes[1];
                                    String dominio = partes[2];

                                    if (diccionario.containsKey(dominio)) {
                                        Optional<Registro> resultado = diccionario.get(dominio).stream()
                                                .filter(r -> r.getTipo().equalsIgnoreCase(tipo))
                                                .findFirst();

                                        if (resultado.isPresent()) {
                                            out.println("200 " + resultado.get().getValor());
                                        } else {
                                            out.println("404 Not Found");
                                        }
                                    } else {
                                        out.println("404 Not Found");
                                    }
                                } else {
                                    out.println("400 Bad request");
                                }
                            } else {
                                out.println("400 Bad request");
                            }
                        } catch (Exception e) {
                            out.println("500 Server error");
                            System.err.println("Error procesando petición: " + e.getMessage());
                        }
                    }

                } catch (IOException e) {
                    System.err.println("Error en conexión cliente: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo iniciar el servidor: " + e.getMessage());
        }
    }

    private static void cargarDiccionario(String nombreFichero) {
        try (BufferedReader br = new BufferedReader(new FileReader(nombreFichero))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String l = linea.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                String[] partes = l.split("\\s+");
                if (partes.length == 3) {
                    String dominio = partes[0];
                    String tipo = partes[1];
                    String valor = partes[2];

                    diccionario.putIfAbsent(dominio, new ArrayList<>());
                    diccionario.get(dominio).add(new Registro(tipo, valor));
                } else {
                    System.out.println("Línea ignorada formato incorrecto: " + linea);
                }
            }
            System.out.println("Diccionario cargado. Dominios: " + diccionario.size());
        } catch (IOException e) {
            System.err.println("Error cargando diccionario: " + e.getMessage());
        }
    }

    private static String cleanLine(String raw) {
        if (raw == null) return "";
        String noAnsi = raw.replaceAll("\\u001B\\[[;?0-9]*[A-Za-z]", "");
        noAnsi = noAnsi.replaceAll("\\u00FF", "");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < noAnsi.length(); i++) {
            char c = noAnsi.charAt(i);
            if (c == '\r') {
                continue;
            } else if (c == '\b' || c == 127) {
                int len = sb.length();
                if (len > 0) sb.deleteCharAt(len - 1);
            } else if (c >= 32 || c == '\t') {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}