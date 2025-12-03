package it.unito.mail.server.net;

import com.google.gson.Gson;
import it.unito.common.Email;
import it.unito.common.Packet;
import it.unito.mail.server.controller.ServerController;
import it.unito.mail.server.model.MailboxManager;

import java.io.*;
import java.net.Socket;
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
                        boolean allRecipientsValid = true;
                        for (String recipient : email.getRecipients()) {
                            if (!model.userExists(recipient)) {
                                allRecipientsValid = false;
                                break;
                            }
                        }

                        if (allRecipientsValid) {
                            for (String recipient : email.getRecipients()) {
                                model.depositEmail(recipient, email);
                            }
                            response.setOutcomeCode("OK");
                            controller.appendLog("Email inviata da " + email.getSender());
                        } else {
                            response.setOutcomeCode("ERROR");
                            response.setOutcomeMessage("Uno o più destinatari non esistono.");
                            controller.appendLog("Errore invio: destinatari non validi.");
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