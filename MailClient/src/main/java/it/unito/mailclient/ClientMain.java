package it.unito.mailclient;

import it.unito.mailclient.controller.ClientController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class ClientMain extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ClientMain.class.getResource("/it/unito/mailclient/client-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);

        ClientController controller = fxmlLoader.getController();

        stage.setTitle("Mail Client");
        stage.setScene(scene);

        stage.setOnCloseRequest(event -> {
            controller.shutdown();
            System.exit(0);
        });

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}