package edu.school21;

import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;

public class Sender {
    private static String idReciever;
    public Sender(Long id) {
        idReciever = id.toString();
    }
    public Object menu(Message message, Long id) {
        String fieldId;
        if (message.hasPhoto()) {
            fieldId = message.getPhoto().get(message.getPhoto().size() - 1).getFileId();
            return sendPhoto(fieldId);
        } else if (message.hasVoice()) {
            fieldId = message.getVoice().getFileId();
            return sendVoice(fieldId);
        } else if (message.hasVideo()) {
            fieldId = message.getVideo().getFileId();
            return sendVideo(fieldId);
        } else if (message.hasAudio()) {
            fieldId = message.getAudio().getFileId();
            return sendAudio(fieldId);
        } else if (message.hasDocument()) {
            fieldId = message.getDocument().getFileId();
            return sendDocument(fieldId);
        } else if (message.hasSticker()) {
            fieldId = message.getSticker().getFileId();
            return sendSticker(fieldId);
        } else {
            return sendMessage("неизвестный контент");
        }
    }

    private static SendMessage sendMessage(String fieldId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(idReciever);
        sendMessage.setText(fieldId);
        return sendMessage;
    }
    private static SendPhoto sendPhoto(String fieldId) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(idReciever);
        sendPhoto.setPhoto(new InputFile(fieldId));
        return sendPhoto;
    }
    private static SendAudio sendAudio(String fieldId) {
        SendAudio sendAudio = new SendAudio();
        sendAudio.setChatId(idReciever);
        sendAudio.setAudio(new InputFile(fieldId));
        return sendAudio;
    }
    private static SendVideo sendVideo(String fieldId) {
        SendVideo sendVideo = new SendVideo();
        sendVideo.setChatId(idReciever);
        sendVideo.setVideo(new InputFile(fieldId));
        return sendVideo;
    }
    private static SendDocument sendDocument(String fieldId) {
        SendDocument sendDocument = new SendDocument();
        sendDocument.setChatId(idReciever);
        sendDocument.setDocument(new InputFile(fieldId));
        return sendDocument;
    }
    private static SendVoice sendVoice(String fieldId) {
        SendVoice sendVoice = new SendVoice();
        sendVoice.setChatId(idReciever);
        sendVoice.setVoice(new InputFile(fieldId));
        return sendVoice;
    }
    private static SendSticker sendSticker(String fieldId) {
        SendSticker sendSticker = new SendSticker();
        sendSticker.setChatId(idReciever);
        sendSticker.setSticker(new InputFile(fieldId));
        return sendSticker;
    }


}
