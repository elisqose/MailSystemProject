package it.unito.mailclient.shared;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class Email implements Serializable {

    // --- LOGICA DI VALIDAZIONE INTEGRATA ---
    // Regex per validare il formato dell'email (es. nome@dominio.com)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    /**
     * Controlla se una stringa è un indirizzo email sintatticamente valido.
     * @param email L'indirizzo da verificare.
     * @return true se valido, false altrimenti.
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    // ---------------------------------------

    private String id;
    private String sender;
    private List<String> recipients;
    private String subject;
    private String text;
    private Date timestamp;

    // Costruttore vuoto necessario per la serializzazione (es. Gson)
    public Email() {}

    // Costruttore principale
    public Email(String sender, List<String> recipients, String subject, String text) {
        this.id = UUID.randomUUID().toString();
        this.sender = sender;
        this.recipients = recipients;
        this.subject = subject;
        this.text = text;
        this.timestamp = new Date(); // Imposta data/ora attuali alla creazione
    }

    // --- GETTERS E SETTERS ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("[%s] Da: %s - Oggetto: %s", timestamp, sender, subject);
    }
}