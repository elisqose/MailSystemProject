module MailClient {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires java.desktop;

    opens it.unito.mailclient to javafx.fxml;
    opens it.unito.mailclient.shared to com.google.gson;
    exports it.unito.mailclient;
    exports it.unito.mailclient.controller;
    opens it.unito.mailclient.controller to javafx.fxml;
}