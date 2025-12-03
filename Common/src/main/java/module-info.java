module Common {
    requires javafx.base;
    requires com.google.gson;

    exports it.unito.common;

    opens it.unito.common to com.google.gson;
}