package it.unito.mail.server.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;

public class ServerController {

    @FXML
    private ListView<String> logList;

    public void initialize() {
        logList.getItems().add("Server inizializzato...");
    }
}