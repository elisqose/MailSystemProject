package it.unito.mailclient.model;

import com.google.gson.Gson;
import it.unito.common.Email;
import it.unito.common.Packet;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class ClientModel {

    // Configurazione Server
    private static final String SERVER_IP = "127.0.0.1"; // Localhost
    private static final int SERVER_PORT = 8189;         // Deve corrispondere al ServerApp

    // Dati dell'utente corrente
    private String userEmailAddress;

    // STEP 5: Teniamo traccia della data dell'ultima mail ricevuta
    // Inizialmente null per scaricare tutto al primo avvio
    private Date lastUpdate = null;

    // Lista observable per il binding con la GUI (TableView/ListView)
    private final ObservableList<Email> inbox;

    // Proprietà per mostrare messaggi di stato o errore nella GUI
    private final StringProperty statusMessage;

    // Regex per validazione email (semplice)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final Gson gson;

    public ClientModel() {
        this.inbox = FXCollections.observableArrayList();
        this.statusMessage = new SimpleStringProperty("In attesa di login...");
        this.gson = new Gson();
    }

    // --- Metodi Getter per la GUI ---

    public ObservableList<Email> getInbox() {
        return inbox;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public String getUserEmailAddress() {
        return userEmailAddress;
    }

    // --- Logica di Business ---

    /**
     * Tenta il login. Verifica la regex e chiede al server se l'utente esiste.
     */
    public boolean login(String email) {
        if (!isValidEmail(email)) {
            setStatus("Formato email non valido!");
            return false;
        }

        // STEP 5: Reset della data all'avvio sessione
        this.lastUpdate = null;
        // Puliamo la lista locale nel caso ci fossero dati di un login precedente
        this.inbox.clear();

        Packet request = new Packet("GET_UPDATES", email);
        // lastUpdate qui è null, quindi il server invierà TUTTO
        request.setLastUpdateDate(null);

        Packet response = sendRequest(request);

        if (response != null && "OK".equals(response.getOutcomeCode())) {
            this.userEmailAddress = email;
            setStatus("Connesso come: " + email);

            // Carica le email iniziali
            List<Email> initialEmails = response.getEmailList();
            if (initialEmails != null) {
                // Usiamo Platform.runLater anche qui per sicurezza sui thread
                Platform.runLater(() -> addNewEmailsLocal(initialEmails));
            }
            return true;
        } else {
            String msg = (response != null && response.getOutcomeMessage() != null)
                    ? response.getOutcomeMessage()
                    : "Errore di connessione o utente non trovato.";
            setStatus(msg);
            return false;
        }
    }

    /**
     * Chiede al server le email aggiornate.
     * Da chiamare periodicamente (es. ogni 5 secondi).
     */
    public void refreshInbox() {
        if (userEmailAddress == null) return;

        Packet request = new Packet("GET_UPDATES", userEmailAddress);

        // STEP 5: Inseriamo la data dell'ultima mail che abbiamo
        // Il server userà questa data per filtrare e mandarci solo le nuove
        request.setLastUpdateDate(this.lastUpdate);

        Packet response = sendRequest(request);

        if (response != null && "OK".equals(response.getOutcomeCode())) {
            List<Email> newEmails = response.getEmailList();

            // Aggiorniamo la GUI solo se ci sono effettivamente nuove mail
            if (newEmails != null && !newEmails.isEmpty()) {
                Platform.runLater(() -> addNewEmailsLocal(newEmails));
            }
        } else {
            // Nota: In caso di errore silenzioso (es. timeout temporaneo)
            // potremmo decidere di non spammare errori all'utente.
            // setStatus("Errore aggiornamento.");
        }
    }

    /**
     * Invia una nuova email.
     */
    public void sendEmail(String recipientsStr, String subject, String text) {
        if (userEmailAddress == null) return;

        // Parsing dei destinatari (separati da virgola o punto e virgola)
        String[] recipientArray = recipientsStr.split("[,;\\s]+");
        List<String> validRecipients = new ArrayList<>();

        for (String r : recipientArray) {
            if (isValidEmail(r)) {
                validRecipients.add(r);
            } else {
                setStatus("Indirizzo destinatario non valido: " + r);
                return; // Interrompi invio se c'è un errore
            }
        }

        if (validRecipients.isEmpty()) {
            setStatus("Nessun destinatario valido specificato.");
            return;
        }

        Email email = new Email(userEmailAddress, validRecipients, subject, text);
        Packet request = new Packet("SEND_EMAIL", userEmailAddress);
        request.setEmail(email);

        Packet response = sendRequest(request);

        if (response != null && "OK".equals(response.getOutcomeCode())) {
            setStatus("Email inviata con successo!");
            // Nota: Non aggiungiamo la mail inviata alla inbox locale
            // perché gestiamo solo la posta in arrivo come da specifiche.
        } else {
            String error = (response != null) ? response.getOutcomeMessage() : "Errore sconosciuto";
            setStatus("Errore invio: " + error);
        }
    }

    /**
     * Richiede la cancellazione di una email.
     */
    public void deleteEmail(Email emailToDelete) {
        if (userEmailAddress == null || emailToDelete == null) return;

        Packet request = new Packet("DELETE_EMAIL", userEmailAddress);
        request.setEmail(emailToDelete); // Il server userà l'ID dell'email

        Packet response = sendRequest(request);

        if (response != null && "OK".equals(response.getOutcomeCode())) {
            setStatus("Email cancellata.");
            // Rimuovi anche localmente subito per feedback immediato
            Platform.runLater(() -> inbox.remove(emailToDelete));
        } else {
            setStatus("Errore cancellazione email.");
        }
    }

    // --- Metodi di Supporto ---

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    private void setStatus(String text) {
        Platform.runLater(() -> statusMessage.set(text));
    }

    /**
     * Aggiunge le NUOVE mail alla lista esistente e riordina.
     * Questo metodo gestisce l'aggiornamento incrementale.
     */
    private void addNewEmailsLocal(List<Email> newEmails) {
        if (newEmails == null || newEmails.isEmpty()) return;

        // Aggiungiamo le nuove mail alla lista esistente
        this.inbox.addAll(newEmails);

        // Ordina per data (più recenti in alto)
        this.inbox.sort(Comparator.comparing(Email::getTimestamp).reversed());

        // STEP 5: Aggiorna il puntatore all'ultima mail ricevuta
        // Poiché abbiamo ordinato decrescente, la prima è la più nuova
        if (!this.inbox.isEmpty()) {
            this.lastUpdate = this.inbox.get(0).getTimestamp();
        }
    }

    /**
     * Gestisce la connessione Socket: Apre -> Invia -> Riceve -> Chiude.
     * Questo rispetta il requisito "Non gestite socket permanenti".
     */
    private Packet sendRequest(Packet request) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // 1. Serializza Richiesta
            String jsonRequest = gson.toJson(request);
            out.println(jsonRequest);

            // 2. Leggi Risposta
            String jsonResponse = in.readLine();
            if (jsonResponse != null) {
                return gson.fromJson(jsonResponse, Packet.class);
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Errore comunicazione Server: " + e.getMessage());
        }
        return null;
    }
}