package it.unito.mailclient.model;

import it.unito.mail.common.Email;
import it.unito.mail.common.Packet;
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
    private final StringProperty connectionState;
    private final StringProperty notificationMessage;
    private final Gson gson;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public ClientModel() {
        this.inbox = FXCollections.observableArrayList();
        this.connectionState = new SimpleStringProperty("Offline");
        this.notificationMessage = new SimpleStringProperty("");
        this.gson = new Gson();
    }

    public ObservableList<Email> getInbox() { return inbox; }
    public StringProperty connectionStateProperty() { return connectionState; }
    public StringProperty notificationMessageProperty() { return notificationMessage; }
    public String getUserEmailAddress() { return userEmailAddress; }

    public boolean login(String email) {
        if (!isValidEmail(email)) {
            setNotification("Formato email non valido!");
            return false;
        }

        this.lastUpdate = null;
        this.inbox.clear();

        Packet request = new Packet("GET_UPDATES", email);
        request.setLastUpdateDate(null);

        Packet response = sendRequest(request);

        if (response != null && "OK".equals(response.getOutcomeCode())) {
            this.userEmailAddress = email;
            setConnectionState("Connesso: " + email);
            setNotification("Login effettuato.");

            List<Email> initialEmails = response.getEmailList();
            if (initialEmails != null) {
                Platform.runLater(() -> addNewEmailsLocal(initialEmails));
            }
            return true;
        } else {
            String msg = (response != null && response.getOutcomeMessage() != null)
                    ? response.getOutcomeMessage()
                    : "Errore login o utente inesistente.";
            setNotification(msg);
            setConnectionState("Offline");
            return false;
        }
    }

    public void logout() {
        this.userEmailAddress = null;
        this.lastUpdate = null;
        this.inbox.clear();
        setConnectionState("Offline");
        setNotification("Disconnesso.");
    }

    public void refreshInbox() {
        if (userEmailAddress == null) return;

        Packet request = new Packet("GET_UPDATES", userEmailAddress);
        request.setLastUpdateDate(this.lastUpdate);

        Packet response = sendRequest(request);

        if (response != null && "OK".equals(response.getOutcomeCode())) {

            Platform.runLater(() -> connectionState.set("Connesso: " + userEmailAddress));

            List<Email> newEmails = response.getEmailList();
            if (newEmails != null && !newEmails.isEmpty()) {
                Platform.runLater(() -> addNewEmailsLocal(newEmails));
            }
        } else {
            Platform.runLater(() -> connectionState.set("Errore di Connessione (Server Offline)"));
        }
    }

    public String sendEmail(String recipientsStr, String subject, String text) {
        if (userEmailAddress == null) return "Non connesso";

        String[] recipientArray = recipientsStr.split("[,;\\s]+");
        List<String> validRecipients = new ArrayList<>();
        List<String> malformedRecipients = new ArrayList<>();

        for (String r : recipientArray) {
            String trimmed = r.trim();
            if (trimmed.isEmpty()) continue;

            if (isValidEmail(trimmed)) {
                if (!validRecipients.contains(trimmed)) validRecipients.add(trimmed);
            } else {
                malformedRecipients.add(trimmed);
            }
        }

        if (validRecipients.isEmpty()) {
            setNotification("Nessun indirizzo email valido inserito.");
            return "Nessun indirizzo email valido inserito.";
        }

        Email email = new Email(userEmailAddress, validRecipients, subject, text);
        Packet request = new Packet("SEND_EMAIL", userEmailAddress);
        request.setEmail(email);

        Packet response = sendRequest(request);

        if (response != null && "OK".equals(response.getOutcomeCode())) {

            if (!malformedRecipients.isEmpty()) {
                String msg = "Email inviata a " + validRecipients.size() + " destinatari.\n" +
                        "Formati errati (ignorata): " + String.join(", ", malformedRecipients);
                setNotification(msg);
                return msg;
            } else {
                setNotification("Email inviata con successo!");
                return null;
            }

        } else if (response != null) {

            String msg = response.getOutcomeMessage();

            if (!malformedRecipients.isEmpty()) {
                msg += "\nFormati errati (ignorata): " + String.join(", ", malformedRecipients);
            }

            setNotification(msg);
            return msg;
        } else {
            String msg = "ERRORE: Server non raggiungibile. Email non inviata";
            setNotification(msg);
            return msg;
        }
    }

    public void deleteEmail(Email emailToDelete) {
        if (userEmailAddress == null || emailToDelete == null) return;

        Packet request = new Packet("DELETE_EMAIL", userEmailAddress);
        request.setEmail(emailToDelete);

        Packet response = sendRequest(request);

        if (response != null && "OK".equals(response.getOutcomeCode())) {
            setNotification("Email cancellata.");
            Platform.runLater(() -> inbox.remove(emailToDelete));
        } else {
            setNotification("Errore cancellazione.");
        }
    }

    public void markEmailAsRead(Email email) {
        if (userEmailAddress == null || email == null) return;
        Packet request = new Packet("MARK_AS_READ", userEmailAddress);
        request.setEmail(email);
        new Thread(() -> sendRequest(request)).start();
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    private void setNotification(String text) {
        Platform.runLater(() -> notificationMessage.set(text));
    }

    private void setConnectionState(String text) {
        Platform.runLater(() -> connectionState.set(text));
    }

    private void addNewEmailsLocal(List<Email> newEmails) {
        if (newEmails == null || newEmails.isEmpty()) return;
        boolean listChanged = false;
        for (Email email : newEmails) {
            boolean alreadyExists = this.inbox.stream().anyMatch(existing -> existing.getId().equals(email.getId()));
            if (!alreadyExists) {
                this.inbox.add(email);
                listChanged = true;
            }
        }
        if (listChanged) {
            this.inbox.sort(Comparator.comparing(Email::getTimestamp).reversed());
            if (!this.inbox.isEmpty()) {
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
            System.err.println("Errore I/O durante la comunicazione: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}