module MailServer {
    requires javafx.controls;
    requires javafx.fxml;
    requires Common;
    requires com.google.gson;

    opens it.unito.mail.server to javafx.fxml;
    opens it.unito.mail.server.controller to javafx.fxml;
    opens it.unito.mail.server.model to javafx.fxml;
    opens it.unito.mail.server.net to javafx.fxml;

    exports it.unito.mail.server;
    exports it.unito.mail.server.controller;
    exports it.unito.mail.server.model;
    exports it.unito.mail.server.net;

}