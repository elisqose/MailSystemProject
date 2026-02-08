package it.unito.mailclient.controller;

import it.unito.mail.common.Email;
import it.unito.mail.common.Packet;
import it.unito.mailclient.model.ClientModel;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.Node;
import javafx.scene.control.TableRow;
import javafx.scene.input.MouseEvent;

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
    @FXML private Label connectionStatusLabel;
    @FXML private Label notificationLabel;
    @FXML private TableView<Email> emailTable;
    @FXML private TableColumn<Email, String> colFrom;
    @FXML private TableColumn<Email, String> colSubject;
    @FXML private TableColumn<Email, Date> colDate;
    @FXML private TextArea messageArea;
    @FXML private Button loginButton;

    private ClientModel model;
    private ScheduledExecutorService autoRefreshService;
    private int unreadNotificationsCount = 0;

    @FXML
    public void initialize() {
        model = new ClientModel();

        errorLabel.textProperty().bind(model.notificationMessageProperty());

        connectionStatusLabel.textProperty().bind(model.connectionStateProperty());
        connectionStatusLabel.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (newVal.contains("Connesso")) {
                    connectionStatusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                } else {
                    connectionStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                }
            }
        });

        notificationLabel.setOnMouseClicked(event -> {
            model.notificationMessageProperty().set("");
            unreadNotificationsCount = 0;
        });

        model.notificationMessageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                notificationLabel.setText(newVal);
                notificationLabel.setCursor(Cursor.HAND);

                boolean isErrorOrWarning = newVal.startsWith("ERRORE") ||
                        newVal.startsWith("Avviso") ||
                        newVal.contains("User inesistenti") ||
                        newVal.contains("Formati errati");

                if (isErrorOrWarning) {
                    notificationLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    unreadNotificationsCount = 0;
                } else if (newVal.startsWith("Hai ricevuto") || newVal.startsWith("Email inviata con successo")) {
                    notificationLabel.setStyle("-fx-text-fill: #0066cc; -fx-font-weight: bold;");
                } else {
                    notificationLabel.setStyle("-fx-text-fill: black;");
                }
            } else {
                notificationLabel.setText("");
                notificationLabel.setCursor(Cursor.DEFAULT);
            }
        });

        emailTable.setItems(model.getInbox());

        model.getInbox().addListener((javafx.collections.ListChangeListener.Change<? extends Email> c) -> {
            while (c.next()) {
                if (c.wasAdded() && inboxPane.isVisible()) {
                    final int newInBatch = c.getAddedSize();

                    Platform.runLater(() -> {
                        java.awt.Toolkit.getDefaultToolkit().beep();

                        unreadNotificationsCount += newInBatch;

                        String message;
                        if (unreadNotificationsCount == 1) {
                            message = "Hai ricevuto 1 nuova mail!";
                        } else {
                            message = "Hai ricevuto " + unreadNotificationsCount + " nuove mail!";
                        }

                        model.notificationMessageProperty().set(message);
                    });
                }
            }
        });

        colFrom.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSender()));
        colFrom.setCellFactory(column -> new TableCell<Email, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    Email email = getTableView().getItems().get(getIndex());
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

        emailTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showEmailDetails(newValue)
        );

        emailTable.setOnMouseClicked(event -> {
            Node node = ((Node) event.getTarget());

            TableRow<?> row = null;
            while (node != null) {
                if (node instanceof TableRow) {
                    row = (TableRow<?>) node;
                    break;
                }
                node = node.getParent();
            }

            if (row == null || row.isEmpty()) {
                emailTable.getSelectionModel().clearSelection();
            }
        });
    }

    @FXML
    protected void onLoginButtonClick() {
        String email = emailField.getText().trim();
        emailField.setDisable(true);
        loginButton.setDisable(true);

        new Thread(() -> {
            boolean success = model.login(email);
            Platform.runLater(() -> {
                if (success) {
                    loginPane.setVisible(false);
                    inboxPane.setVisible(true);
                    currentUserLabel.setText("Utente: " + email);
                    startAutoRefresh();
                }
                else {
                    emailField.setDisable(false);
                    loginButton.setDisable(false);
                }
            });
        }).start();
    }

    private void startAutoRefresh() {
        if (autoRefreshService != null && !autoRefreshService.isShutdown()) {
            autoRefreshService.shutdownNow();
        }
        autoRefreshService = Executors.newScheduledThreadPool(1);
        autoRefreshService.scheduleAtFixedRate(() -> {
            model.refreshInbox();
        }, 5, 5, TimeUnit.SECONDS);
    }

    @FXML
    protected void onDeleteButtonClick() {
        Email selected = emailTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            new Thread(() -> {
                model.deleteEmail(selected);
                Platform.runLater(() -> {
                    messageArea.clear();
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
            String subject = "Re: " + selected.getSubject();
            showComposeDialog(new Email(selected.getSender(), null, subject, ""), "Rispondi");
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
        if (selected != null) {
            String subject = "Fwd: " + selected.getSubject();
            String body = "\n\n--- Inoltrato ---\n" + selected.getText();
            showComposeDialog(new Email("", null, subject, body), "Inoltra");
        }

        else {
            showAlert("Attenzione", "Seleziona una mail da inoltrare.");
        }
    }

    private void showEmailDetails(Email email) {
        if (email != null) {
            messageArea.setText(email.getText());
            if (!email.isRead()) {
                email.setRead(true);
                emailTable.refresh();
                model.markEmailAsRead(email);

                if (unreadNotificationsCount > 0) {
                    unreadNotificationsCount--;

                    if (unreadNotificationsCount == 0) {
                        model.notificationMessageProperty().set("");
                    } else {
                        String msg = (unreadNotificationsCount == 1)
                                ? "Hai 1 nuova mail!"
                                : "Hai " + unreadNotificationsCount + " nuove mail!";
                        model.notificationMessageProperty().set(msg);
                    }
                }
            }
        }
        else {
            messageArea.clear();
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
        toField.setPromptText("Destinatari (separati da virgola)");
        TextField subjectField = new TextField();
        subjectField.setPromptText("Oggetto");
        TextArea bodyArea = new TextArea();
        bodyArea.setPromptText("Messaggio...");

        if (draft != null) {
            if (draft.getSender() != null) toField.setText(draft.getSender());
            if (draft.getSubject() != null) subjectField.setText(draft.getSubject());
            if (draft.getText() != null) bodyArea.setText(draft.getText());
        }

        layout.getChildren().addAll(new Label("A:"), toField, new Label("Oggetto:"), subjectField, new Label("Testo:"), bodyArea);
        dialog.getDialogPane().setContent(layout);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == sendButtonType) {
            new Thread(() -> {
                model.sendEmail(toField.getText(), subjectField.getText(), bodyArea.getText());
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
        shutdown();
        model.logout();
        unreadNotificationsCount = 0;
        inboxPane.setVisible(false);
        loginPane.setVisible(true);
        emailField.clear();
        messageArea.clear();
        emailField.setDisable(false);
        loginButton.setDisable(false);
    }

    public void shutdown() {
        if (autoRefreshService != null) {
            autoRefreshService.shutdownNow();
        }
    }
}