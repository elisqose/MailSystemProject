package it.unito.mail.server;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ServerApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Caricheremo l'interfaccia da file FXML (lo faremo tra poco)
        FXMLLoader fxmlLoader = new FXMLLoader(ServerApp.class.getResource("server-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);

        stage.setTitle("Mail Server - Log");
        stage.setScene(scene);

        // Quando chiudi la finestra, il server deve spegnersi davvero
        stage.setOnCloseRequest(event -> {
            System.exit(0);
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch(); // Avvia JavaFX
    }
}