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
            // 1. Leggi la richiesta JSON (assumiamo una riga per richiesta per semplicità)
            // Nota: Se il JSON è formattato su più righe, Gson può leggere direttamente dallo stream,
            // ma per i socket testuali è spesso più sicuro leggere l'intero oggetto o usare un delimitatore.
            // Qui usiamo il parser di Gson direttamente sullo stream che è più robusto.

            // Per semplicità di debug, leggiamo la stringa intera (attenzione ai buffer molto grandi)
            String jsonRequest = in.readLine();
            if (jsonRequest == null) return;

            Packet request = gson.fromJson(jsonRequest, Packet.class);
            Packet response = new Packet();

            String cmd = request.getCommand();
            String user = request.getUserEmailAddress();

            controller.appendLog("Richiesta da " + socket.getInetAddress() + ": " + cmd);

            // --- GESTIONE COMANDI ---
            switch (cmd) {
                case "SEND_EMAIL":
                    Email email = request.getEmail();
                    // Verifica mittente e destinatari
                    if (email != null && model.userExists(email.getSender())) {
                        boolean allRecipientsValid = true;
                        for (String recipient : email.getRecipients()) {
                            if (!model.userExists(recipient)) {
                                allRecipientsValid = false;
                                break;
                            }
                        }

                        if (allRecipientsValid) {
                            // Salva per ogni destinatario
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

                case "GET_UPDATES": // O "LOGIN"
                    if (model.userExists(user)) {
                        List<Email> inbox = model.getInbox(user);
                        response.setEmailList(inbox);
                        response.setOutcomeCode("OK");
                        controller.appendLog("Aggiornamento inviato a " + user);
                    } else {
                        response.setOutcomeCode("ERROR");
                        response.setOutcomeMessage("Utente sconosciuto.");
                    }
                    break;

                case "DELETE_EMAIL":
                    Email emailToDelete = request.getEmail(); // O passa solo l'ID se preferisci modificare Packet
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

            // 2. Invia la risposta
            String jsonResponse = gson.toJson(response);
            out.println(jsonResponse);

        } catch (IOException e) {
            controller.appendLog("Errore I/O Client: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }
}