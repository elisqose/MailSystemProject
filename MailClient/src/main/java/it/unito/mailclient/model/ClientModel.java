package it.unito.mailclient.model;

import it.unito.mailclient.shared.Email;
import it.unito.mailclient.shared.Packet;

import com.google.gson.Gson;
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

    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8189;

    private String userEmailAddress;
    private Date lastUpdate = null;
    private final ObservableList<Email> inbox;
    private final StringProperty statusMessage;
    private final Gson gson;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public ClientModel() {
        this.inbox = FXCollections.observableArrayList();
        this.statusMessage = new SimpleStringProperty("In attesa di login...");
        this.gson = new Gson();
    }

    public ObservableList<Email> getInbox() {
        return inbox;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public String getUserEmailAddress() {
        return userEmailAddress;
    }

    public boolean login(String email) {
        if (!isValidEmail(email)) {
            setStatus("Formato email non valido!");
            return false;
        }

        this.lastUpdate = null;
        this.inbox.clear();

        Packet request = new Packet("GET_UPDATES", email);
        request.setLastUpdateDate(null);

        Packet response = sendRequest(request);

        if (response != null && "OK".equals(response.getOutcomeCode())) {
            this.userEmailAddress = email;
            setStatus("Connesso come: " + email);

            List<Email> initialEmails = response.getEmailList();
            if (initialEmails != null) {
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

    public void logout() {
        this.userEmailAddress = null;
        this.lastUpdate = null;
        this.inbox.clear();
        setStatus("Disconnesso.");
    }

    public void refreshInbox() {
        if (userEmailAddress == null) return;

        Packet request = new Packet("GET_UPDATES", userEmailAddress);
        request.setLastUpdateDate(this.lastUpdate);

        Packet response = sendRequest(request);

        if (response != null && "OK".equals(response.getOutcomeCode())) {
            // Ripristino stato se eravamo offline (Riconnessione Automatica)
            if (statusMessage.get().contains("non raggiungibile")) {
                setStatus("Connesso come: " + userEmailAddress);
            }

            List<Email> newEmails = response.getEmailList();
            if (newEmails != null && !newEmails.isEmpty()) {
                Platform.runLater(() -> addNewEmailsLocal(newEmails));
            }
        }
    }

    public void sendEmail(String recipientsStr, String subject, String text) {
        if (userEmailAddress == null) return;

        String[] recipientArray = recipientsStr.split("[,;\\s]+");
        List<String> validRecipients = new ArrayList<>();

        for (String r : recipientArray) {
            String trimmed = r.trim();
            if (trimmed.isEmpty()) continue;
            if (isValidEmail(trimmed)) {
                if (!validRecipients.contains(trimmed)) validRecipients.add(trimmed);
            } else {
                setStatus("Indirizzo destinatario non valido: " + trimmed);
                return;
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
        } else if (response != null) {
            // Qui gestiamo i messaggi d'errore del server (es: destinatari inesistenti)
            setStatus("Avviso: " + response.getOutcomeMessage());
        }
    }

    public void deleteEmail(Email emailToDelete) {
        if (userEmailAddress == null || emailToDelete == null) return;

        Packet request = new Packet("DELETE_EMAIL", userEmailAddress);
        request.setEmail(emailToDelete);

        Packet response = sendRequest(request);

        if (response != null && "OK".equals(response.getOutcomeCode())) {
            setStatus("Email cancellata.");
            Platform.runLater(() -> inbox.remove(emailToDelete));
        } else if (response != null) {
            setStatus("Errore cancellazione: " + response.getOutcomeMessage());
        }
    }

    /**
     * Comunica al server che un'email è stata letta per garantirne la persistenza.
     */
    public void markEmailAsRead(Email email) {
        if (userEmailAddress == null || email == null) return;

        Packet request = new Packet("MARK_AS_READ", userEmailAddress);
        request.setEmail(email);

        // Invio in un nuovo thread per non bloccare l'interfaccia durante la lettura
        new Thread(() -> {
            sendRequest(request);
        }).start();
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    private void setStatus(String text) {
        Platform.runLater(() -> statusMessage.set(text));
    }

    private void addNewEmailsLocal(List<Email> newEmails) {
        if (newEmails == null || newEmails.isEmpty()) return;

        boolean listChanged = false;
        for (Email email : newEmails) {
            boolean alreadyExists = this.inbox.stream()
                    .anyMatch(existing -> existing.getId().equals(email.getId()));

            if (!alreadyExists) {
                this.inbox.add(email);
                listChanged = true;
            }
        }

        if (listChanged) {
            this.inbox.sort(Comparator.comparing(Email::getTimestamp).reversed());
            if (!this.inbox.isEmpty()) {
                // Aggiorniamo il timestamp dell'ultimo messaggio per i futuri GET_UPDATES
                this.lastUpdate = this.inbox.get(0).getTimestamp();
            }
        }
    }

    private Packet sendRequest(Packet request) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(gson.toJson(request));

            String jsonResponse = in.readLine();
            if (jsonResponse != null) {
                return gson.fromJson(jsonResponse, Packet.class);
            }

        } catch (IOException e) {
            // Feedback per la riconnessione automatica (Trasparenza)
            setStatus("ERRORE: Server non raggiungibile. Tentativo di riconnessione...");
        }
        return null;
    }
}