package it.unito.mail.server.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import it.unito.mail.server.shared.Email;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MailboxManager {

    private static final String[] VALID_USERS = {"user1@mail.com", "user2@mail.com", "user3@mail.com"};
    private static final String DATA_DIR = "ServerData";
    private final Gson gson;

    private static MailboxManager instance;

    private MailboxManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        initializeStorage();
    }

    public static synchronized MailboxManager getInstance() {
        if (instance == null) {
            instance = new MailboxManager();
        }
        return instance;
    }

    private void initializeStorage() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
            for (String user : VALID_USERS) {
                Path userFile = Paths.get(DATA_DIR, user + ".json");
                if (!Files.exists(userFile)) {
                    saveListToFile(userFile, new ArrayList<>());
                    System.out.println("Creata mailbox per: " + user);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean userExists(String emailAddress) {
        for (String user : VALID_USERS) {
            if (user.equalsIgnoreCase(emailAddress)) return true;
        }
        return false;
    }

    public synchronized void depositEmail(String recipient, Email email) throws IOException {
        Path path = Paths.get(DATA_DIR, recipient + ".json");
        List<Email> inbox = loadListFromFile(path);
        inbox.add(email);
        saveListToFile(path, inbox);
    }

    public synchronized List<Email> getInbox(String user) {
        if (!userExists(user)) return Collections.emptyList();
        try {
            Path path = Paths.get(DATA_DIR, user + ".json");
            return loadListFromFile(path);
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public synchronized List<Email> getInbox(String user, java.util.Date since) {
        List<Email> allEmails = getInbox(user);

        if (since == null) {
            return allEmails;
        }

        List<Email> newEmails = new ArrayList<>();
        for (Email e : allEmails) {
            if (e.getTimestamp().after(since)) {
                newEmails.add(e);
            }
        }
        return newEmails;
    }

    public synchronized void deleteEmail(String user, String emailId) throws IOException {
        Path path = Paths.get(DATA_DIR, user + ".json");
        List<Email> inbox = loadListFromFile(path);
        // Rimuove la mail se l'ID corrisponde
        inbox.removeIf(e -> e.getId().equals(emailId));
        saveListToFile(path, inbox);
    }

    private List<Email> loadListFromFile(Path path) throws IOException {
        if (!Files.exists(path)) return new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path)) {
            Type listType = new TypeToken<ArrayList<Email>>(){}.getType();
            List<Email> list = gson.fromJson(reader, listType);
            return list != null ? list : new ArrayList<>();
        }
    }

    private void saveListToFile(Path path, List<Email> list) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            gson.toJson(list, writer);
        }
    }
}