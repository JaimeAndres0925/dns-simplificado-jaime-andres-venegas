package src.main.java;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LeerCliente implements Runnable {
    private final Socket socket;
    private final Map<String, List<Registro>> diccionario;
    String[] partes;


    public LeerCliente(Socket socket, Map<String, List<Registro>> diccionario) {
        this.socket = socket;
        this.diccionario = diccionario;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String linea;
            while ((linea = in.readLine()) != null) {
                try {
                    if (linea.equalsIgnoreCase("EXIT")) {
                        out.println("Bye!");
                        break;
                    } else if (linea.startsWith("LOOKUP")) {
                         partes = linea.split("\\s+");
                    } else if (linea.equalsIgnoreCase("LIST")) {
                        out.println("150 Inicio listado");
                        for (Map.Entry<String, List<Registro>> entry : diccionario.entrySet()) {
                            String dominio = entry.getKey();
                            for (Registro registro : entry.getValue()) {
                                System.out.println(dominio + " " + registro.getTipo() + " " + registro.getValor());
                            }
                        }
                        System.out.println("226 Fin listado");



                    if (partes.length == 3) {
                            String tipo = partes[1];
                            String dominio = partes[2];
                            if (diccionario.containsKey(dominio)) {
                                Optional<Registro> resultado = diccionario.get(dominio).stream().filter(r -> r.getTipo().equalsIgnoreCase(tipo)).findFirst();

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
                }
            }
        } catch (IOException e) {
            System.out.println("Error en cliente: " + e.getMessage());
        }
    }
}


