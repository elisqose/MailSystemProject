package it.unito.mailclient.controller;

import it.unito.mailclient.shared.Email;
import it.unito.mailclient.model.ClientModel;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

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

        colFrom.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSender()));
        colSubject.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSubject()));
        colDate.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().getTimestamp()));

        emailTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showEmailDetails(newValue)
        );
    }

    @FXML
    protected void onLoginButtonClick() {
        String email = emailField.getText().trim();
        boolean success = model.login(email);

        if (success) {
            loginPane.setVisible(false);
            inboxPane.setVisible(true);
            currentUserLabel.setText("Utente: " + email);

            startAutoRefresh();
        }
    }

    @FXML
    protected void onDeleteButtonClick() {
        Email selected = emailTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            model.deleteEmail(selected);
            messageArea.clear();
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
        } else {
            messageArea.clear();
        }
    }

    private void startAutoRefresh() {
        autoRefreshService = Executors.newScheduledThreadPool(1);
        autoRefreshService.scheduleAtFixedRate(() -> {
            model.refreshInbox();
        }, 0, 5, TimeUnit.SECONDS); // Ogni 5 secondi
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
            if (draft.getSender() != null && !draft.getSender().isEmpty()) toField.setText(draft.getSender()); // Per reply
            if (draft.getSubject() != null) subjectField.setText(draft.getSubject());
            if (draft.getText() != null) bodyArea.setText(draft.getText());
        }

        layout.getChildren().addAll(new Label("Destinatari:"), toField, new Label("Oggetto:"), subjectField, new Label("Testo:"), bodyArea);
        dialog.getDialogPane().setContent(layout);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == sendButtonType) {
            model.sendEmail(toField.getText(), subjectField.getText(), bodyArea.getText());
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