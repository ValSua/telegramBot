package es.codegym.telegrambot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyFirstTelegramBot extends MultiSessionTelegramBot {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();
    public static final String NAME = "MyCarAssistant_bot";
    public static final String TOKEN = "7970533267:AAGtExPUZj5CrBSEFHAXt360cbSC4Ffdkqc";

    // Mapa para almacenar números de teléfono
    private Map<Long, String> userPhoneNumbers = new HashMap<>();
    private Map<Long, ConversationState> userStates = new HashMap<>();

    private enum ConversationState {
        AWAITING_INITIAL_QUESTION,
        AWAITING_FOLLOW_UP
    }

    public MyFirstTelegramBot() {
        super(NAME, TOKEN);
    }

    @Override
    public void onUpdateEventReceived(Update update) {
        Long chatId = update.getMessage().getChatId();

        // Si el usuario compartió su número de teléfono
        if (update.getMessage().hasContact()) {
            String phoneNumber = update.getMessage().getContact().getPhoneNumber();

            if (phoneNumber.startsWith("57") && phoneNumber.length() > 10) {
                phoneNumber = phoneNumber.substring(2);
            }

            userPhoneNumbers.put(chatId, phoneNumber);
            userStates.put(chatId, ConversationState.AWAITING_INITIAL_QUESTION);
            sendTextMessageAsync(chatId, "Gracias por compartir tu número de teléfono: " + phoneNumber);

            //sendTextMessageAsync("Gracias por compartir tu número de teléfono: " + phoneNumber);
            removeKeyboard(chatId);
            return;
        }

        // Verificar si ya tenemos el número de teléfono del usuario
        String phoneNumber = userPhoneNumbers.get(chatId);

        if (phoneNumber == null) {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String userName = update.getMessage().getFrom().getUserName();
                if (userName == null || userName.isEmpty()) {
                    userName = update.getMessage().getFrom().getFirstName();
                }
                String responseMessage = "¡Hola " + userName + "! Para comenzar, por favor déjame saber tu número de teléfono:";
                requestPhoneNumber(chatId, responseMessage);
            }
        } else {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String userMessage = update.getMessage().getText();
                processUserMessage(chatId, phoneNumber, userMessage);
            }
        }
    }

    private void processUserMessage(Long chatId, String phoneNumber, String userMessage) {
        try {
            ApiResponse apiResponse = callApi(phoneNumber, userMessage);
            String responseMessage = formatApiResponse(apiResponse);
            sendTextMessageAsync(chatId, responseMessage);

            // Enviar mensaje de seguimiento y actualizar el estado de la conversación
            sendTextMessageAsync(chatId, "¿Te puedo ayudar en algo más?");
            userStates.put(chatId, ConversationState.AWAITING_FOLLOW_UP);
        } catch (Exception e) {
            sendTextMessageAsync(chatId, "Error al procesar tu solicitud: " + e.getMessage());
        }
    }

    private void sendTextMessageAsync(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private ApiResponse callApi(String phoneNumber, String question) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(Map.of(
                "cel", phoneNumber,
                "question", question
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:9999/api/graph/"))
                .header("Content-Type", "application/json")
                .header("accept", "application/json")
                .header("Authorization", "Token 5e55a5653c9634d01a99e085498355d7bb4f81ac")
                .header("X-CSRFTOKEN", "bRrEBNED6jsY4iuc6He0YIXGyVxAH0BOBnYxsAR4Kt5s5P1MNIN9rsX7SUVm3AiK")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return new ApiResponse(response.statusCode(), response.body());
    }

    private String formatApiResponse(ApiResponse apiResponse) throws Exception {
        if (apiResponse.statusCode == 201) {
            JsonNode jsonNode = objectMapper.readTree(apiResponse.body);
            String id = jsonNode.path("id").asText();
            String answer = jsonNode.path("answer").asText();
            return String.format("Solicitud procesada con éxito.\nID: %s\nRespuesta: %s", id, answer);
        } else {
            return "La API respondió con el código de estado: " + apiResponse.statusCode;
        }
    }

    private static class ApiResponse {
        final int statusCode;
        final String body;

        ApiResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    // Método para solicitar el número de teléfono con un mensaje personalizado
    private void requestPhoneNumber(Long chatId, String personalizedMessage) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(personalizedMessage);

        // Crear el botón para solicitar el número de teléfono
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        // Agregar el botón para compartir el número de teléfono
        KeyboardButton button = new KeyboardButton();
        button.setText("Compartir mi número de teléfono");
        button.setRequestContact(true);  // Aquí solicitamos el número de teléfono
        row.add(button);

        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        // Enviar el mensaje con el teclado
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Método para eliminar el teclado después de recibir el número
    private void removeKeyboard(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("¿En qué te puedo ayudar?");

        // Crear el objeto para eliminar el teclado
        ReplyKeyboardRemove removeKeyboard = new ReplyKeyboardRemove();
        removeKeyboard.setRemoveKeyboard(true);

        message.setReplyMarkup(removeKeyboard);

        // Enviar el mensaje sin teclado
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(new MyFirstTelegramBot());
    }
}


