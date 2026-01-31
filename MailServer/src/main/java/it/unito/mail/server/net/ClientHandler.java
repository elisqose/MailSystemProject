package it.unito.mail.server.net;

import com.google.gson.Gson;
import it.unito.mail.server.shared.Email;
import it.unito.mail.server.shared.Packet;
import it.unito.mail.server.controller.ServerController;
import it.unito.mail.server.model.MailboxManager;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {

    private Socket socket;
    private ServerController controller;
    private MailboxManager model;
    private Gson gson;

    public ClientHandler(Socket socket, ServerController controller) {
        this.socket = socket;
        this.controller = controller;
        this.model = MailboxManager.getInstance();
        this.gson = new Gson();
    }

    @Override
    public void run() {
        try (
                InputStreamReader isr = new InputStreamReader(socket.getInputStream());
                BufferedReader in = new BufferedReader(isr);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {

            String jsonRequest = in.readLine();
            if (jsonRequest == null) return;

            Packet request = gson.fromJson(jsonRequest, Packet.class);
            Packet response = new Packet();

            String cmd = request.getCommand();
            String user = request.getUserEmailAddress();

            controller.appendLog("Richiesta da " + socket.getInetAddress() + ": " + cmd);

            switch (cmd) {
                case "SEND_EMAIL":
                    Email email = request.getEmail();

                    if (email != null && model.userExists(email.getSender())) {
                        List<String> invalidRecipients = new ArrayList<>();
                        List<String> validRecipients = new ArrayList<>();

                        // 1. Separiamo i destinatari validi da quelli inesistenti
                        for (String recipient : email.getRecipients()) {
                            if (model.userExists(recipient)) {
                                validRecipients.add(recipient);
                            } else {
                                invalidRecipients.add(recipient);
                            }
                        }

                        // 2. Inviamo ai destinatari validi (se presenti)
                        for (String recipient : validRecipients) {
                            model.depositEmail(recipient, email);
                        }

                        // 3. Prepariamo la risposta per il client
                        if (invalidRecipients.isEmpty()) {
                            response.setOutcomeCode("OK");
                            controller.appendLog("Email inviata con successo da " + email.getSender());
                        } else {
                            // Se ci sono errori, segnaliamo quali indirizzi hanno fallito
                            response.setOutcomeCode("PARTIAL_ERROR"); // Nuovo codice opzionale o mantieni ERROR
                            String errorMsg = "Email non consegnata a: " + String.join(", ", invalidRecipients);
                            response.setOutcomeMessage(errorMsg);

                            controller.appendLog("Invio parziale da " + email.getSender() + ". Errori: " + invalidRecipients);

                            // Se non c'era nemmeno un destinatario valido, l'esito è un fallimento totale
                            if (validRecipients.isEmpty()) {
                                response.setOutcomeCode("ERROR");
                            }
                        }
                    } else {
                        response.setOutcomeCode("ERROR");
                        response.setOutcomeMessage("Mittente non valido o dati mancanti.");
                    }
                    break;

                case "GET_UPDATES":
                    if (model.userExists(user)) {
                        java.util.Date clientLastDate = request.getLastUpdateDate();

                        List<Email> updates = model.getInbox(user, clientLastDate);

                        response.setEmailList(updates);
                        response.setOutcomeCode("OK");

                        if (!updates.isEmpty()) {
                            controller.appendLog("Inviati " + updates.size() + " nuovi messaggi a " + user);
                        }
                    } else {
                        response.setOutcomeCode("ERROR");
                        response.setOutcomeMessage("Utente sconosciuto.");
                    }
                    break;

                case "DELETE_EMAIL":
                    Email emailToDelete = request.getEmail();
                    if (emailToDelete != null && model.userExists(user)) {
                        model.deleteEmail(user, emailToDelete.getId());
                        response.setOutcomeCode("OK");
                        controller.appendLog("Email " + emailToDelete.getId() + " cancellata da " + user);
                    } else {
                        response.setOutcomeCode("ERROR");
                        response.setOutcomeMessage("Errore cancellazione.");
                    }
                    break;

                case "MARK_AS_READ":
                    Email emailToMark = request.getEmail();
                    if (emailToMark != null && model.userExists(user)) {
                        // Aggiorna lo stato sul server per rendere la lettura persistente
                        model.markAsRead(user, emailToMark.getId());
                        response.setOutcomeCode("OK");
                        controller.appendLog("Email " + emailToMark.getId() + " segnata come letta da " + user);
                    } else {
                        response.setOutcomeCode("ERROR");
                        response.setOutcomeMessage("Errore durante l'aggiornamento dello stato lettura.");
                    }
                    break;

                default:
                    response.setOutcomeCode("ERROR");
                    response.setOutcomeMessage("Comando sconosciuto");
            }

            String jsonResponse = gson.toJson(response);
            out.println(jsonResponse);

        } catch (IOException e) {
            controller.appendLog("Errore I/O Client: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }
}