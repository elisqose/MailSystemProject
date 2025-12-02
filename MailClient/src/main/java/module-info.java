module MailClient {
    requires javafx.controls;
    requires javafx.fxml;
    requires Common;
    requires com.google.gson;

    opens it.unito.mail.client to javafx.fxml;
    exports it.unito.mailclient;
}