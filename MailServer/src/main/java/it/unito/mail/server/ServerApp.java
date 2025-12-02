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

    // Porta su cui il server ascolterà (deve essere la stessa usata dal Client)
    private static final int PORT = 8189;

    @Override
    public void start(Stage stage) throws IOException {
        // 1. Carica l'interfaccia FXML
        // NOTA: Assicurati che "server-view.fxml" sia in src/main/resources/it/unito/mail/server/
        FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource("server-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);

        // 2. Recupera il Controller creato dal FXMLLoader
        // Questo ci serve per passare l'istanza del controller al thread di rete
        ServerController controller = fxmlLoader.getController();

        // 3. Imposta la GUI
        stage.setTitle("Mail Server - Log");
        stage.setScene(scene);

        // 4. Gestione chiusura: Quando chiudi la finestra, termina tutto (inclusi i thread)
        stage.setOnCloseRequest(event -> {
            System.out.println("Chiusura server in corso...");
            System.exit(0);
        });

        stage.show();

        // 5. Avvia il Server in un Thread separato (per non bloccare la GUI)
        startServer(controller);
    }

    private void startServer(ServerController controller) {
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {

                // Scrive nel log della GUI che il server è partito
                controller.appendLog("Server avviato e in ascolto sulla porta " + PORT);

                while (true) {
                    // Rimane in attesa di una connessione (bloccante)
                    Socket clientSocket = serverSocket.accept();

                    // Appena arriva un client, crea un gestore (ClientHandler)
                    // e gli passa il socket e il controller per il log
                    ClientHandler handler = new ClientHandler(clientSocket, controller);

                    // Avvia il gestore in un nuovo thread
                    new Thread(handler).start();
                }

            } catch (IOException e) {
                controller.appendLog("Errore Server: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // Imposta il thread come "Daemon" così si chiude se l'app principale termina
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public static void main(String[] args) {
        launch();
    }
}