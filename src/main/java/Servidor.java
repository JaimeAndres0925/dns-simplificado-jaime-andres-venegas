package src.main.java;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Servidor {
    private static final Map<String, List<Registro>> diccionario = new HashMap<>();
    private static final String FICHERO = "dominios.txt";
    private static final int PUERTO = 5000;
    private static final int MAX_HILOS = 5;
    private static final Object FILE_LOCK = new Object(); // el lock para escritura en fichero
    private static final Object MAP_LOCK = new Object();  // el lock para acceso al diccionario

    public static void main(String[] args) {
        cargarDiccionario(FICHERO);

        ExecutorService pool = Executors.newFixedThreadPool(MAX_HILOS);
        try (ServerSocket server = new ServerSocket(PUERTO)) {
            System.out.println("Servidor simple sincronizado en puerto " + PUERTO + " (pool " + MAX_HILOS + ")");
            while (true) {
                Socket client = server.accept();
                pool.submit(() -> manejarCliente(client));
            }
        } catch (IOException e) {
            System.err.println("Error servidor: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    private static void manejarCliente(Socket socket) {
        String clienteInfo = socket.getRemoteSocketAddress().toString();
        System.out.println("Conexión: " + clienteInfo);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

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
                        synchronized (MAP_LOCK) {
                            for (Map.Entry<String, List<Registro>> e : diccionario.entrySet()) {
                                String dominio = e.getKey();
                                List<Registro> lista = e.getValue();
                                for (Registro r : lista) {
                                    out.println(dominio + " " + r.getTipo() + " " + r.getValor());
                                }
                            }
                        }
                        out.println("226 Fin listado");
                    }

                    else if (linea.toUpperCase().startsWith("LOOKUP")) {
                        String[] p = linea.split("\\s+");
                        if (p.length == 3) {
                            String tipo = p[1];
                            String dominio = p[2];
                            boolean found = false;
                            synchronized (MAP_LOCK) {
                                List<Registro> lista = diccionario.get(dominio);
                                if (lista != null) {
                                    for (Registro r : lista) {
                                        if (r.getTipo().equalsIgnoreCase(tipo)) {
                                            out.println("200 " + r.getValor());
                                            found = true;
                                        }
                                    }
                                }
                            }
                            if (!found) out.println("404 Not Found");
                        } else {
                            out.println("400 Bad request");
                        }
                    }

                    else if (linea.toUpperCase().startsWith("REGISTER")) {
                        String[] p = linea.split("\\s+");
                        if (p.length == 4) {
                            String dominio = p[1];
                            String tipo = p[2];
                            String valor = p[3];

                            synchronized (MAP_LOCK) {
                                diccionario.putIfAbsent(dominio, new ArrayList<>());
                                diccionario.get(dominio).add(new Registro(tipo, valor));
                            }

                            boolean escrito = false;
                            synchronized (FILE_LOCK) {
                                try (FileWriter fw = new FileWriter(FICHERO, true);
                                     BufferedWriter bw = new BufferedWriter(fw);
                                     PrintWriter pw = new PrintWriter(bw)) {
                                    pw.println(dominio + " " + tipo + " " + valor);
                                    escrito = true;
                                } catch (IOException ioe) {
                                    escrito = false;
                                }
                            }

                            if (escrito) out.println("200 Record added");
                            else {
                                // revertir en memoria si falla la escritura
                                synchronized (MAP_LOCK) {
                                    List<Registro> lista = diccionario.get(dominio);
                                    if (lista != null && !lista.isEmpty()) {
                                        lista.remove(lista.size() - 1);
                                        if (lista.isEmpty()) diccionario.remove(dominio);
                                    }
                                }
                                out.println("500 Server error");
                            }
                        } else {
                            out.println("400 Bad request");
                        }
                    }

                    else {
                        out.println("400 Bad request");
                    }
                } catch (Exception ex) {
                    out.println("500 Server error");
                    System.err.println("Error procesando petición: " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error cliente " + clienteInfo + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("Desconectado: " + clienteInfo);
        }
    }

    private static void cargarDiccionario(String fichero) {
        File f = new File(fichero);
        if (!f.exists()) {
            System.out.println("Fichero no existe, se creará al registrar: " + fichero);
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(fichero))) {
            String linea;
            synchronized (MAP_LOCK) {
                while ((linea = br.readLine()) != null) {
                    String l = linea.trim();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    String[] p = l.split("\\s+");
                    if (p.length == 3) {
                        String dominio = p[0];
                        String tipo = p[1];
                        String valor = p[2];
                        diccionario.putIfAbsent(dominio, new ArrayList<>());
                        diccionario.get(dominio).add(new Registro(tipo, valor));
                    } else {
                        System.out.println("Ignorada: " + linea);
                    }
                }
            }
            System.out.println("Diccionario cargado. Dominios: " + diccionario.size());
        } catch (IOException e) {
            System.err.println("Error cargando fichero: " + e.getMessage());
        }
    }

    private static String cleanLine(String raw) {
        if (raw == null) return "";
        String noAnsi = raw.replaceAll("\\u001B\\[[;?0-9]*[A-Za-z]", "");
        noAnsi = noAnsi.replaceAll("\\u00FF", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < noAnsi.length(); i++) {
            char c = noAnsi.charAt(i);
            if (c == '\r') continue;
            else if (c == '\b' || c == 127) {
                int len = sb.length();
                if (len > 0) sb.deleteCharAt(len - 1);
            } else if (c >= 32 || c == '\t') sb.append(c);
        }
        return sb.toString().trim();
    }
}