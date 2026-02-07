module Common {
    requires com.google.gson;
    exports it.unito.mail.common;
    opens it.unito.mail.common to com.google.gson;
}