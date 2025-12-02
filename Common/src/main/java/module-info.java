module Common {
    requires javafx.base; // Serve per le ObservableList
    requires com.google.gson;

    // Rende visibile la classe Email agli altri moduli
    exports it.unito.mail.common;

    // Permette a Gson di leggere/scrivere la classe Email
    opens it.unito.mail.common to com.google.gson;
}