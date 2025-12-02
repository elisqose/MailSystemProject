package it.unito.mail.server.controller;

import javafx.application.Platform; // <--- Importante!
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

public class ServerController {

    @FXML
    private ListView<String> logList;

    public void initialize() {
        if(logList != null) {
            appendLog("Server GUI inizializzata.");
        }
    }

    // Ecco il metodo che ti mancava
    public void appendLog(String text) {
        Platform.runLater(() -> {
            if (logList != null) {
                logList.getItems().add(text);
                logList.scrollTo(logList.getItems().size() - 1); // Scorre in basso auto
            }
        });
    }
}