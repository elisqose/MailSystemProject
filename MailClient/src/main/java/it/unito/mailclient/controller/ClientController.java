package it.unito.mailclient.controller;

import it.unito.mailclient.shared.Email; // Assicurati che il package sia corretto
import it.unito.mailclient.model.ClientModel;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientController {

    @FXML private VBox loginPane;
    @FXML private BorderPane inboxPane;

    @FXML private TextField emailField;
    @FXML private Label errorLabel;
    @FXML private Label currentUserLabel;
    @FXML private Label statusLabel;

    @FXML private TableView<Email> emailTable;
    @FXML private TableColumn<Email, String> colFrom;
    @FXML private TableColumn<Email, String> colSubject;
    @FXML private TableColumn<Email, Date> colDate;

    @FXML private TextArea messageArea;

    private ClientModel model;
    private ScheduledExecutorService autoRefreshService;

    @FXML
    public void initialize() {
        model = new ClientModel();

        errorLabel.textProperty().bind(model.statusMessageProperty());
        statusLabel.textProperty().bind(model.statusMessageProperty());

        emailTable.setItems(model.getInbox());

        // 1. LISTENER NOTIFICHE (Senza Pop-up)
        model.getInbox().addListener((javafx.collections.ListChangeListener.Change<? extends Email> c) -> {
            while (c.next()) {
                if (c.wasAdded() && inboxPane.isVisible()) {
                    // Nessun Alert intrusivo.
                    // Opzionale: puoi stampare in console o fare un suono di sistema
                    System.out.println("Nuovi messaggi ricevuti: " + c.getAddedSize());
                }
            }
        });

        // 2. CONFIGURAZIONE COLONNE E PALLINO ROSSO
        colFrom.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSender()));

        // Custom Cell Factory per disegnare il pallino rosso
        colFrom.setCellFactory(column -> new TableCell<Email, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item); // Mostra il mittente

                    Email email = getTableView().getItems().get(getIndex());

                    // Se NON è letta, mostra pallina rossa
                    if (!email.isRead()) {
                        setGraphic(new Circle(4, Color.RED));
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        colSubject.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSubject()));
        colDate.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getTimestamp()));

        // Gestione selezione messaggio
        emailTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showEmailDetails(newValue)
        );
    }

    @FXML
    protected void onLoginButtonClick() {
        String email = emailField.getText().trim();

        // Feedback immediato
        model.statusMessageProperty().set("Connessione in corso...");

        // Thread separato per evitare freeze della GUI
        new Thread(() -> {
            boolean success = model.login(email);

            Platform.runLater(() -> {
                if (success) {
                    loginPane.setVisible(false);
                    inboxPane.setVisible(true);
                    currentUserLabel.setText("Utente: " + email);
                    startAutoRefresh();
                }
            });
        }).start();
    }

    @FXML
    protected void onDeleteButtonClick() {
        Email selected = emailTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            model.statusMessageProperty().set("Cancellazione in corso...");

            new Thread(() -> {
                model.deleteEmail(selected);
                Platform.runLater(() -> {
                    messageArea.clear();
                    model.statusMessageProperty().set("Email cancellata.");
                });
            }).start();
        } else {
            showAlert("Nessuna selezione", "Seleziona una mail da cancellare.");
        }
    }

    @FXML
    protected void onWriteButtonClick() {
        showComposeDialog(null, "Nuovo Messaggio");
    }

    @FXML
    protected void onReplyButtonClick() {
        Email selected = emailTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            String recipient = selected.getSender();
            String subject = "Re: " + selected.getSubject();
            showComposeDialog(new Email(recipient, null, subject, ""), "Rispondi");
        } else {
            showAlert("Attenzione", "Seleziona una mail a cui rispondere.");
        }
    }

    @FXML
    protected void onReplyAllButtonClick() {
        Email selected = emailTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            StringBuilder recipients = new StringBuilder(selected.getSender());

            // Aggiungi tutti i destinatari tranne me stesso e il mittente originale (che è già gestito)
            for (String r : selected.getRecipients()) {
                if (!r.equalsIgnoreCase(model.getUserEmailAddress()) && !r.equalsIgnoreCase(selected.getSender())) {
                    recipients.append(", ").append(r);
                }
            }

            String subject = "Re: " + selected.getSubject();
            showComposeDialog(new Email(recipients.toString(), null, subject, ""), "Rispondi a Tutti");
        } else {
            showAlert("Attenzione", "Seleziona una mail a cui rispondere.");
        }
    }

    @FXML
    protected void onForwardButtonClick() {
        Email selected = emailTable.getSelectionModel().getSelectedItem();
        if(selected != null) {
            String subject = "Fwd: " + selected.getSubject();
            String body = "\n\n--- Inoltrato ---\n" + selected.getText();
            showComposeDialog(new Email("", null, subject, body), "Inoltra");
        }
    }

    private void showEmailDetails(Email email) {
        if (email != null) {
            messageArea.setText(email.getText());

            // 3. SEGNA COME LETTO
            if (!email.isRead()) {
                email.setRead(true);
                // Aggiorna la tabella per rimuovere il pallino rosso
                emailTable.refresh();
            }
        } else {
            messageArea.clear();
        }
    }

    private void startAutoRefresh() {
        autoRefreshService = Executors.newScheduledThreadPool(1);
        autoRefreshService.scheduleAtFixedRate(() -> {
            model.refreshInbox();
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (autoRefreshService != null) {
            autoRefreshService.shutdownNow();
        }
    }

    private void showComposeDialog(Email draft, String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Componi Email");

        ButtonType sendButtonType = new ButtonType("Invia", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(sendButtonType, ButtonType.CANCEL);

        VBox layout = new VBox(10);
        TextField toField = new TextField();
        toField.setPromptText("A: (destinatari separati da virgola)");

        TextField subjectField = new TextField();
        subjectField.setPromptText("Oggetto");

        TextArea bodyArea = new TextArea();
        bodyArea.setPromptText("Messaggio...");

        if (draft != null) {
            if (draft.getSender() != null && !draft.getSender().isEmpty()) toField.setText(draft.getSender());
            if (draft.getSubject() != null) subjectField.setText(draft.getSubject());
            if (draft.getText() != null) bodyArea.setText(draft.getText());
        }

        layout.getChildren().addAll(new Label("Destinatari:"), toField, new Label("Oggetto:"), subjectField, new Label("Testo:"), bodyArea);
        dialog.getDialogPane().setContent(layout);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == sendButtonType) {
            String to = toField.getText();
            String subj = subjectField.getText();
            String txt = bodyArea.getText();

            model.statusMessageProperty().set("Invio messaggio in corso...");

            new Thread(() -> {
                model.sendEmail(to, subj, txt);
                // Status aggiornato automaticamente dal model
            }).start();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    protected void onLogoutButtonClick() {
        if (autoRefreshService != null && !autoRefreshService.isShutdown()) {
            autoRefreshService.shutdownNow();
        }

        model.logout();

        inboxPane.setVisible(false);
        loginPane.setVisible(true);

        emailField.clear();
    }
}