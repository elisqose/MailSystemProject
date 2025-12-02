package it.unito.mailclient;

import it.unito.mailclient.controller.ClientController; // Importa il controller
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class ClientMain extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ClientMain.class.getResource("/it/unito/mailclient/client-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600); // Aumentato un po' le dimensioni

        // Recuperiamo il controller per poterlo chiudere dopo
        ClientController controller = fxmlLoader.getController();

        stage.setTitle("Mail Client");
        stage.setScene(scene);

        // Quando l'utente chiude la finestra (X rossa)
        stage.setOnCloseRequest(event -> {
            controller.shutdown(); // Ferma il thread del timer
            System.exit(0);        // Chiude tutto
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}