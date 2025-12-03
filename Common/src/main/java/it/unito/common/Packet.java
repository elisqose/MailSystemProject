package it.unito.common;

import java.io.Serializable;
import java.util.List;

public class Packet implements Serializable {
    private String command;

    private Email email;

    private String userEmailAddress;

    private List<Email> emailList;

    private String outcomeCode;
    private String outcomeMessage;

    private java.util.Date lastUpdateDate;

    public Packet() {}

    public Packet(String command, String userEmailAddress) {
        this.command = command;
        this.userEmailAddress = userEmailAddress;
    }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public Email getEmail() { return email; }
    public void setEmail(Email email) { this.email = email; }

    public String getUserEmailAddress() { return userEmailAddress; }
    public void setUserEmailAddress(String userEmailAddress) { this.userEmailAddress = userEmailAddress; }

    public List<Email> getEmailList() { return emailList; }
    public void setEmailList(List<Email> emailList) { this.emailList = emailList; }

    public String getOutcomeCode() { return outcomeCode; }
    public void setOutcomeCode(String outcomeCode) { this.outcomeCode = outcomeCode; }

    public String getOutcomeMessage() { return outcomeMessage; }
    public void setOutcomeMessage(String outcomeMessage) { this.outcomeMessage = outcomeMessage; }

    public java.util.Date getLastUpdateDate() { return lastUpdateDate; }
    public void setLastUpdateDate(java.util.Date lastUpdateDate) { this.lastUpdateDate = lastUpdateDate; }
}