package src.main.java;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Servidor {
    private static Map <String, List<Registro>> diccionario = new HashMap<String, List<Registro>>();
    public static void main(String[] args) {
        cargarDiccionario("dominios.txt");
        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            System.out.println("Servidor esperando conexiones...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Cliente conectado.");
                new Thread(new LeerCliente(socket, diccionario)).start();
            }
        } catch (IOException e) {
            System.out.println("Servidor desconectado." + e.getMessage());;
        }

    }
    private static void cargarDiccionario(String nombreFichero) {
        try (BufferedReader br = new BufferedReader(new FileReader(nombreFichero))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split("\\s+");
                if (partes.length == 3) {
                    String dominio = partes[0];
                    String tipo = partes[1];
                    String valor = partes[2];

                    diccionario.putIfAbsent(dominio, new ArrayList<>());
                    diccionario.get(dominio).add(new Registro(tipo, valor));
                }
            }
        } catch (IOException e) {
            System.out.println("Error cargando diccionario: " + e.getMessage());
        }
    }
}


