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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MailboxManager {

    private static final String[] VALID_USERS = {"user1@mail.com", "user2@mail.com", "user3@mail.com"};
    private static final String DATA_DIR = "ServerData";
    private final Gson gson;

    // Mappa per gestire lock granulari per ogni utente (Scalabilità)
    private final Map<String, ReadWriteLock> userLocks = new ConcurrentHashMap<>();

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

    /**
     * Ritorna il lock specifico per l'utente, creandolo se non esiste.
     */
    private ReadWriteLock getUserLock(String email) {
        return userLocks.computeIfAbsent(email.toLowerCase(), k -> new ReentrantReadWriteLock());
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

    // SCRITTURA: Usa Lock di Scrittura specifico per utente
    public void depositEmail(String recipient, Email email) throws IOException {
        ReadWriteLock lock = getUserLock(recipient);
        lock.writeLock().lock();
        try {
            Path path = Paths.get(DATA_DIR, recipient + ".json");
            List<Email> inbox = loadListFromFile(path);
            inbox.add(email);
            saveListToFile(path, inbox);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // LETTURA: Usa Lock di Lettura specifico per utente (Condiviso)
    public List<Email> getInbox(String user) {
        if (!userExists(user)) return Collections.emptyList();

        ReadWriteLock lock = getUserLock(user);
        lock.readLock().lock();
        try {
            Path path = Paths.get(DATA_DIR, user + ".json");
            return loadListFromFile(path);
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    // LETTURA: Thread-safe tramite getInbox(user)
    public List<Email> getInbox(String user, java.util.Date since) {
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

    // SCRITTURA: Usa Lock di Scrittura specifico per utente
    public void deleteEmail(String user, String emailId) throws IOException {
        ReadWriteLock lock = getUserLock(user);
        lock.writeLock().lock();
        try {
            Path path = Paths.get(DATA_DIR, user + ".json");
            List<Email> inbox = loadListFromFile(path);

            boolean removed = inbox.removeIf(e -> e.getId().equals(emailId));

            if (removed) {
                saveListToFile(path, inbox);
            }
        } finally {
            lock.writeLock().unlock();
        }
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

    // SCRITTURA: Usa Lock di Scrittura specifico per utente
    public void markAsRead(String user, String emailId) throws IOException {
        ReadWriteLock lock = getUserLock(user);
        lock.writeLock().lock();
        try {
            Path path = Paths.get(DATA_DIR, user + ".json");
            List<Email> inbox = loadListFromFile(path);

            boolean updated = false;
            for (Email e : inbox) {
                if (e.getId().equals(emailId)) {
                    e.setRead(true);
                    updated = true;
                    break;
                }
            }

            if (updated) {
                saveListToFile(path, inbox);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}