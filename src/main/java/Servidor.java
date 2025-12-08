package src.main.java;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Servidor {
    private static final Map<String, List<Registro>> diccionario = new HashMap<>();
    private static final String FICHERO = "dominios.txt";
    private static final int PUERTO = 5000;

    public static void main(String[] args) {
        cargarDiccionario(FICHERO);

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            System.out.println("Servidor escuchando en puerto " + PUERTO);
            while (true) {
                try (Socket socket = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                    System.out.println("Cliente conectado: " + socket.getRemoteSocketAddress());
                    out.println("220 Bienvenido. Comandos: LOOKUP <tipo> <dominio> | LIST | REGISTER <dominio> <tipo> <valor> | EXIT");

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
                            }
                            else if (linea.toUpperCase().startsWith("LOOKUP")) {
                                String[] partes = linea.split("\\s+");
                                if (partes.length == 3) {
                                    String tipo = partes[1];
                                    String dominio = partes[2];
                                    if (diccionario.containsKey(dominio)) {
                                        List<Registro> resultados = new ArrayList<>();
                                        for (Registro r : diccionario.get(dominio)) {
                                            if (r.getTipo().equalsIgnoreCase(tipo)) {
                                                resultados.add(r);
                                            }
                                        }
                                        if (!resultados.isEmpty()) {
                                            for (Registro r : resultados) {
                                                out.println("200 " + r.getValor());
                                            }
                                        } else {
                                            out.println("404 Not Found");
                                        }
                                    } else {
                                        out.println("404 Not Found");
                                    }
                                } else {
                                    out.println("400 Bad request");
                                }
                            }
                            else if (linea.toUpperCase().startsWith("REGISTER")) {
                                String[] partes = linea.split("\\s+");
                                if (partes.length == 4) {
                                    String dominio = partes[1];
                                    String tipo = partes[2];
                                    String valor = partes[3];

                                    diccionario.putIfAbsent(dominio, new ArrayList<>());
                                    diccionario.get(dominio).add(new Registro(tipo, valor));

                                    try (FileWriter fw = new FileWriter(FICHERO, true);
                                         BufferedWriter bw = new BufferedWriter(fw);
                                         PrintWriter pw = new PrintWriter(bw)) {
                                        pw.println(dominio + " " + tipo + " " + valor);
                                    } catch (IOException ioe) {List<Registro> lista = diccionario.get(dominio);
                                        if (lista != null && !lista.isEmpty()) {
                                            lista.remove(lista.size() - 1);
                                            if (lista.isEmpty()) {
                                                diccionario.remove(dominio);
                                            }
                                        }
                                        out.println("500 Server error");
                                        System.err.println("Error escribiendo en fichero: " + ioe.getMessage());
                                        continue;
                                    }

                                    out.println("200 Record added");
                                } else {
                                    out.println("400 Bad request");
                                }
                            }
                            else {
                                out.println("400 Bad request");
                            }
                        } catch (Exception e) {
                            out.println("500 Server error");
                            System.err.println("Error procesando petición: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    System.out.println("Cliente desconectado: " + socket.getRemoteSocketAddress());
                } catch (IOException e) {
                    System.err.println("Error en conexión cliente: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo iniciar el servidor: " + e.getMessage());
        }
    }

    private static void cargarDiccionario(String nombreFichero) {
        File f = new File(nombreFichero);
        if (!f.exists()) {
            System.out.println("Fichero no existe, se creará al registrar nuevos registros: " + nombreFichero);
            return;
        }

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
                    System.out.println("Línea ignorada (formato incorrecto): " + linea);
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