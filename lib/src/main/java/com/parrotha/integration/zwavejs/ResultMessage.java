package com.parrotha.integration.zwavejs;

import java.util.Map;

public class ResultMessage {
    boolean success;
    String messageId;
    Map result;

    public ResultMessage() {
    }

    public ResultMessage(boolean success, String messageId, Map result) {
        this.success = success;
        this.messageId = messageId;
        this.result = result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Map getResult() {
        return result;
    }

    public void setResult(Map result) {
        this.result = result;
    }
}
