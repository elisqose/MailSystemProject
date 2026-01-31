package it.unito.mail.server;

import it.unito.mail.server.controller.ServerController;
import it.unito.mail.server.net.ClientHandler;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerApp extends Application {

    private static final int PORT = 8189;

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource("server-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);

        ServerController controller = fxmlLoader.getController();

        stage.setTitle("Mail Server - Log");
        stage.setScene(scene);

        stage.setOnCloseRequest(event -> {
            System.out.println("Chiusura server in corso...");
            System.exit(0);
        });

        stage.show();

        startServer(controller);
    }

    private void startServer(ServerController controller) {
        Thread serverThread = new Thread(() -> {
            // 1. CREAZIONE DEL THREAD POOL
            // Usiamo 'newCachedThreadPool': crea nuovi thread se necessario,
            // ma riutilizza quelli precedentemente costruiti se sono disponibili.
            ExecutorService threadPool = Executors.newCachedThreadPool();

            try (ServerSocket serverSocket = new ServerSocket(PORT)) {

                controller.appendLog("Server avviato e in ascolto sulla porta " + PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();

                    ClientHandler handler = new ClientHandler(clientSocket, controller);

                    // 2. ESECUZIONE TRAMITE IL POOL
                    // Invece di 'new Thread(handler).start()', passiamo il compito al pool
                    threadPool.execute(handler);
                }

            } catch (IOException e) {
                controller.appendLog("Errore Server: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // 3. CHIUSURA PULITA
                // Se il serverSocket si chiude (o c'è un errore fatale),
                // chiudiamo anche il pool di thread per liberare le risorse.
                threadPool.shutdown();
            }
        });

        serverThread.setDaemon(true);
        serverThread.start();
    }

    public static void main(String[] args) {
        launch();
    }
}