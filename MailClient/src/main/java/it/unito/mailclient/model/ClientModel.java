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

    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8189;

    private String userEmailAddress;

    private Date lastUpdate = null;

    private final ObservableList<Email> inbox;

    private final StringProperty statusMessage;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final Gson gson;

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
            List<Email> newEmails = response.getEmailList();

            if (newEmails != null && !newEmails.isEmpty()) {
                Platform.runLater(() -> addNewEmailsLocal(newEmails));
            }
        } else {

        }
    }

    public void sendEmail(String recipientsStr, String subject, String text) {
        if (userEmailAddress == null) return;

        String[] recipientArray = recipientsStr.split("[,;\\s]+");

        List<String> validRecipients = new ArrayList<>();

        for (String r : recipientArray) {
            if (r.trim().isEmpty()) continue;

            if (isValidEmail(r)) {

                if (!validRecipients.contains(r)) {
                    validRecipients.add(r);
                }
            } else {
                setStatus("Indirizzo destinatario non valido: " + r);
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

        } else {
            String error = (response != null && response.getOutcomeMessage() != null)
                    ? response.getOutcomeMessage()
                    : "Errore sconosciuto";
            setStatus("Errore invio: " + error);
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
        } else {
            setStatus("Errore cancellazione email.");
        }
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
                this.lastUpdate = this.inbox.get(0).getTimestamp();
            }
        }
    }

    private Packet sendRequest(Packet request) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String jsonRequest = gson.toJson(request);
            out.println(jsonRequest);

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