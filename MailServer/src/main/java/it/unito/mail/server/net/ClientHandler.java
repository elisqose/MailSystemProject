package it.unito.mail.server.net;

import com.google.gson.Gson;
import it.unito.mail.common.Email;
import it.unito.mail.common.Packet;
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

            if (cmd != null) {
                controller.appendLog("Richiesta da " + socket.getInetAddress() + ": " + cmd);
            }

            switch (cmd) {
                case "SEND_EMAIL":
                    Email email = request.getEmail();

                    if (email != null && model.userExists(email.getSender())) {
                        List<String> invalidRecipients = new ArrayList<>();
                        List<String> validRecipients = new ArrayList<>();

                        for (String recipient : email.getRecipients()) {
                            if (model.userExists(recipient)) {
                                validRecipients.add(recipient);
                            } else {
                                invalidRecipients.add(recipient);
                            }
                        }

                        for (String recipient : validRecipients) {
                            try {
                                model.depositEmail(recipient, email);
                            } catch (IOException e) {
                                controller.appendLog("Errore scrittura su file per: " + recipient);
                                invalidRecipients.add(recipient + " (Errore IO)");
                            }
                        }

                        if (invalidRecipients.isEmpty()) {
                            response.setOutcomeCode("OK");
                            response.setOutcomeMessage("Email inviata con successo.");
                            controller.appendLog("Email inviata con successo da " + email.getSender());
                        } else {
                            if (validRecipients.isEmpty()) {
                                response.setOutcomeCode("ERROR");
                                response.setOutcomeMessage("Invio fallito. Nessun destinatario valido trovato.");
                                controller.appendLog("Invio fallito da " + email.getSender() + ": nessun destinatario valido.");
                            } else {
                                response.setOutcomeCode("PARTIAL_ERROR");
                                String msg = "Email inviata a " + validRecipients.size() + " destinatari.\n" +
                                        "User inesistenti (non consegnata): " + String.join(", ", invalidRecipients);
                                response.setOutcomeMessage(msg);

                                controller.appendLog("Invio parziale da " + email.getSender() + ". Validi: " +
                                        validRecipients.size() + ", Errati: " + invalidRecipients);
                            }
                        }
                    } else {
                        response.setOutcomeCode("ERROR");
                        response.setOutcomeMessage("Mittente non riconosciuto o dati mancanti.");
                    }
                    break;

                case "GET_UPDATES":
                    if (user != null && model.userExists(user)) {
                        java.util.Date clientLastDate = request.getLastUpdateDate();

                        List<Email> updates = model.getInbox(user, clientLastDate);

                        response.setEmailList(updates);
                        response.setOutcomeCode("OK");

                        if (!updates.isEmpty()) {
                            controller.appendLog("Inviati " + updates.size() + " nuovi messaggi a " + user);
                        }
                    } else {
                        response.setOutcomeCode("ERROR");
                        response.setOutcomeMessage("Utente sconosciuto: " + user);
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
                        response.setOutcomeMessage("Errore cancellazione: dati non validi.");
                    }
                    break;

                case "MARK_AS_READ":
                    Email emailToMark = request.getEmail();
                    if (emailToMark != null && model.userExists(user)) {
                        model.markAsRead(user, emailToMark.getId());
                        response.setOutcomeCode("OK");
                        controller.appendLog("Email " + emailToMark.getId() + " segnata come letta da " + user);
                    } else {
                        response.setOutcomeCode("ERROR");
                        response.setOutcomeMessage("Errore aggiornamento stato lettura.");
                    }
                    break;

                default:
                    response.setOutcomeCode("ERROR");
                    response.setOutcomeMessage("Comando sconosciuto: " + cmd);
            }

            String jsonResponse = gson.toJson(response);
            out.println(jsonResponse);

        } catch (IOException e) {
            if (controller != null) {
                controller.appendLog("Errore connessione client: " + e.getMessage());
            }
            e.printStackTrace();
        } finally {
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
            }
        }
    }
}