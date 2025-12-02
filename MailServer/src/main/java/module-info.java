module MailServer {
    requires javafx.controls;
    requires javafx.fxml;
    requires Common; // Richiede il nostro modulo comune
    requires com.google.gson; // Per i JSON

    // Apre il package grafico a JavaFX per caricare la GUI
    opens it.unito.mail.server to javafx.fxml;

    // Esporta il package principale per poterlo avviare
    exports it.unito.mail.server;
}