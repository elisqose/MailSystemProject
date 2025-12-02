module MailClient {
    requires javafx.controls;
    requires javafx.fxml;
    requires Common;
    requires com.google.gson;

    opens it.unito.mailclient to javafx.fxml;
    exports it.unito.mailclient;
    exports it.unito.mailclient.controller;
    opens it.unito.mailclient.controller to javafx.fxml;
}