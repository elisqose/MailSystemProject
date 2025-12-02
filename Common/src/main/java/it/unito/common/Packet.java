package it.unito.common;

import java.io.Serializable;
import java.util.List;

/**
 * Classe contenitore per la comunicazione JSON tra Client e Server.
 * Rappresenta una richiesta o una risposta.
 */
public class Packet implements Serializable {
    // Tipo di comando (es. "LOGIN", "SEND_EMAIL", "DELETE_EMAIL", "GET_INBOX")
    private String command;

    // Argomenti del comando (opzionali)
    // Esempio: per "SEND_EMAIL", qui ci sarà l'oggetto Email serializzato o nidificato
    private Email email;

    // Esempio: per "LOGIN" o "GET_INBOX", serve l'identificativo dell'utente
    private String userEmailAddress;

    // Esempio: per inviare una lista di email (risposta del server alla richiesta di aggiornamento)
    private List<Email> emailList;

    // Codice di stato o messaggio di errore (utile per le risposte del Server)
    // "OK", "ERROR", "USER_NOT_FOUND"
    private String outcomeCode;
    private String outcomeMessage;

    // Costruttore vuoto per Gson
    public Packet() {}

    // Costruttore di comodo per richieste semplici (es. richiesta aggiornamenti)
    public Packet(String command, String userEmailAddress) {
        this.command = command;
        this.userEmailAddress = userEmailAddress;
    }

    // --- Getters e Setters ---

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public Email getEmail() { return email; }
    public void setEmail(Email email) { this.email = email; }

    public String getUserEmailAddress() { return userEmailAddress; }
    public void setUserEmailAddress(String userEmailAddress) { this.userEmailAddress = userEmailAddress; }

    public List<Email> getEmailList() { return emailList; }
    public void setEmailList(List<Email> emailList) { this.emailList = emailList; }

    public String getOutcomeCode() { return outcomeCode; }
    public void setOutcomeCode(String outcomeCode) { this.outcomeCode = outcomeCode; }

    public String getOutcomeMessage() { return outcomeMessage; }
    public void setOutcomeMessage(String outcomeMessage) { this.outcomeMessage = outcomeMessage; }
}