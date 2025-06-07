package edu.school21;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class ExpenseBot extends TelegramLongPollingBot {
    private final String BOT_USERNAME = "";
    private final String BOT_TOKEN = "";
    private String invite = "Выберите необходимое, нажав на кнопки ниже \uD83D\uDC47";
    private String empty = "\uD83E\uDD37 База данных пуста!";
    private String addExpense = "Добавить расход";
    private String resultText = "Результаты";
    private String invalidFormatNeedsNumber = "";
    private String symbolAgree = "\u2714";
    private String symbolNotAgree = "\u2716";
    private Map<Long, ConversationState> userStates = new HashMap<>();
    private Map<Long, Pair<String, Optional<String>>> pendingExpenseName = new HashMap<>();
    private Map<Long, List<Expense>> userExpenses = new HashMap<>();
    private Map<String, Double> category = new HashMap<>();
    private DataBaseManager db = new DataBaseManager();
    private LocalDateTime parsedDateStart;
    private LocalDateTime parsedDateFinish;
    private DateParser dateParser = new DateParser();
    private List<Expense> sendExpenses;
    private double amount;
    private String expenseName;
    private String expenseAbstraction;
    private boolean isToday = true;
    private Expense expense;
    private Long id;
    private Integer lastMessageId;


    @Override
    public void onUpdateReceived(Update update) {
        category = DataBaseManager.getCategories();
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }

        if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();
            ConversationState state = userStates.getOrDefault(chatId, ConversationState.NONE);
            if (update.getMessage().hasText()) {
                String text = update.getMessage().getText().trim();
                if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase(".старт")) {
                    userStates.put(chatId, ConversationState.NONE);
                    menuWithTwoButtons(chatId, invite, addExpense, "add_expense", resultText, "results");
                    sendMainMenu(chatId);
                } else if (text.equalsIgnoreCase(".инфо")) {
                    userStates.put(chatId, ConversationState.NONE);
                    boolean res = toExcel(chatId, true);
                    if (!res) sendText(chatId, empty);
                    menuWithTwoButtons(chatId, invite, addExpense, "add_expense", resultText, "results");
                } else if (text.equalsIgnoreCase(".удалить")) {
                    userStates.put(chatId, ConversationState.AWAITING_ID_TO_DELETE);
                    sendText(chatId, "\u0037\uFE0F\u20E3 Напишите id расхода:");
                } else if (state == ConversationState.AWAITING_ID_TO_DELETE) {
                    try {
                        id = Long.parseLong(text);
                        expense = db.findExpenseById(id);
                        if (expense == null) {
                            sendText(chatId, "\uD83E\uDD37 \"" + "Расход с номером " + id + "\" в базе отсутствует!");
                            userStates.put(chatId, ConversationState.NONE);
                            menuWithTwoButtons(chatId, invite, addExpense, "add_expense", resultText, "results");
                        } else {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
                            menuWithTwoButtons(chatId, "Подтверждаете ли удаление с базы \n[№" + id + "\t\'" + expense.getName() + "\':\t" + expense.getAbstraction() + "\t" + expense.getAmount() + "₽\t" + expense.getDate().format(formatter) + "\t" +
                                    expense.getOwnerId() + "]\n?", symbolAgree, "agree_del", symbolNotAgree, "no_agree_del");//❌
                        }
                    } catch (NumberFormatException e) {
                        sendText(chatId, invalidFormatNeedsNumber);
                    }
                } else if (state == ConversationState.AWAITING_EXPENSE_ABSTRACT) {
                    Pair<String, Optional<String>> currentData = pendingExpenseName.get(chatId);
                    currentData.setSecond(Optional.of(text));
                    userStates.put(chatId, ConversationState.AWAITING_EXPENSE_AMOUNT);
                    sendText(chatId, "\u0037\uFE0F\u20E3 Напишите сумму расхода:");
                } else if (state == ConversationState.AWAITING_EXPENSE_AMOUNT) {
                    try {
                        amount = Double.parseDouble(text);
                        expenseName = pendingExpenseName.get(chatId).getFirst();
                        expenseAbstraction = getExpenseCategory(chatId);
                        menuWithTwoButtons(chatId, "Подтверждаете ли добавление в категорию \"" + expenseName + "\" расхода с названием " + "\"" + expenseAbstraction + "\", с суммой " +
                                amount + "?", symbolAgree, "agree", symbolNotAgree, "no_agree");//❌
                    } catch (NumberFormatException e) {
                        sendText(chatId, invalidFormatNeedsNumber);
                    }
                } else if (state == ConversationState.AWAITING_NEW_CATEGORY) {
                    String newCategory = normalizeText(text);  // Нормализуем текст
                    if (!category.containsKey(newCategory)) {
                        category.put(newCategory, 0.0);  // Добавляем новую категорию
                        sendText(chatId, "\u2705 Категория \"" + newCategory + "\" была добавлена.");
                    }
                    userStates.put(chatId, ConversationState.NONE);
                    sendCategories(chatId);  // Показываем список категорий снова
                } else if (state == ConversationState.AWAITING_DATE_START) {
                    AtomicInteger message = new AtomicInteger(-1);
                    parsedDateStart = dateParser.parseDate(text, message);
                    if (parsedDateStart == null) {
                        String sms = typesErrors(message);
                        if (sms != null) sendText(chatId, typesErrors(message));
                        deleteMessage(chatId, update.getMessage().getMessageId());
                    } else {
                        sendText(chatId, "Напришите _*вторую*_ дату в формате ДД.ММ.ГГГ:");
                        userStates.put(chatId, ConversationState.AWAITING_DATE_FINISH);
                    }
                } else if (state == ConversationState.AWAITING_DATE_FINISH) {
                    AtomicInteger message = new AtomicInteger(-1);
                    parsedDateFinish = dateParser.parseDate(text, message);
                    if (parsedDateFinish == null || parsedDateStart.isAfter(parsedDateFinish)) {
                        String sms = typesErrors(message);
                        if (sms != null) sendText(chatId, typesErrors(message));
                        else if (parsedDateFinish != null) sendText(chatId, "\u2757Первая дата должна быть раньше второй");
                        deleteMessage(chatId, update.getMessage().getMessageId());
                    } else {
                        parsedDateFinish = parsedDateFinish.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
                        System.out.println(parsedDateStart + " " + parsedDateFinish);
                        sendExpenses = DataBaseManager.getExpensesForPeriod(parsedDateStart, parsedDateFinish);
                        sendExpenseReport(chatId, sendExpenses, "Результаты " + parsedDateStart.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + " - " +
                                parsedDateFinish.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                        userStates.put(chatId, ConversationState.NONE);
                    }
                } else if (text.equalsIgnoreCase("намаз сегодня") || text.equalsIgnoreCase("намаз бүген") ||
                        text.equalsIgnoreCase("намаз завтра") || text.equalsIgnoreCase("намаз иртәгә")) {
                    userStates.put(chatId, ConversationState.AWAITING_PRAYER_CITY);
                    if (text.equalsIgnoreCase("намаз сегодня") || text.equalsIgnoreCase("намаз бүген")) isToday = true;
                    else isToday = false;
                    sendText(chatId, "\u0037\uFE0F\u20E3 Название населенного пункта:");
                } else if (state == ConversationState.AWAITING_PRAYER_CITY) {
                    String excelUrl = "https://dumrt.ru/netcat_files/multifile/2649/namaz_vakytlary24_25_isp.07.12.2023.xlsx"; // Заменить на реальный URL
                    String result = ExcelParser.findRowsByTextAndDate(excelUrl, text, isToday);
                    sendText(chatId, result);
                    userStates.put(chatId, ConversationState.NONE);
                } else { // Состояние NONE
                    deleteMessage(chatId, update.getMessage().getMessageId());
                }
            } else { // Состояние NONE
                deleteMessage(chatId, update.getMessage().getMessageId());
            }
        }
    }
    public String getExpenseCategory(long chatId) {
        Pair<String, Optional<String>> data = pendingExpenseName.get(chatId);
        return data != null && data.getSecond().isPresent()
                ? data.getSecond().get() // Если категория присутствует, возвращаем её
                : "Not Set"; // Если категория отсутствует, возвращаем сообщение
    }

    private Integer sendText(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setText(escapeMarkdownV2(text));
        message.setParseMode("MarkdownV2");
        Message postMessage = executeSend(message);
        if (postMessage == null) return -1;
        return postMessage.getMessageId();
    }

    private Message executeSend(Object sendObject) {
        try {
            if (sendObject instanceof SendMessage) {
                return execute((SendMessage) sendObject);
            } else if (sendObject instanceof SendPhoto) {
                return execute((SendPhoto) sendObject);
            } else if (sendObject instanceof SendAudio) {
                return execute((SendAudio) sendObject);
            } else if (sendObject instanceof SendVoice) {
                return execute((SendVoice) sendObject);
            } else if (sendObject instanceof SendVideo) {
                return execute((SendVideo) sendObject);
            } else if (sendObject instanceof SendDocument) {
                return execute((SendDocument) sendObject);
            } else if (sendObject instanceof SendSticker) {
                return execute((SendSticker) sendObject);
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        return null;
    }
    private void sendMainMenu(long chatId) {
        // Создаем клавиатуру
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        // Первая строка с кнопками
        KeyboardRow row1 = new KeyboardRow();
        row1.add("Намаз сегодня");
        row1.add("Намаз завтра");

        // Добавляем строки в клавиатуру
        keyboard.add(row1);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true); // Кнопки будут уменьшаться под размер текста
//        keyboardMarkup.setOneTimeKeyboard(true); // Скрыть клавиатуру после выбора

        // Отправляем сообщение с обычной клавиатурой
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(".");
        sendMessage.setReplyMarkup(keyboardMarkup);

        try {
            execute(sendMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private String typesErrors(AtomicInteger message) {
        if (message.get() == 1) return "\u2757Есть ошибка в дате";
        else if (message.get() == 2) return "\u2757Введенная дата еще не наступила";
        else return null;
    }
    private String escapeMarkdownV2(String text) {
        return text.replaceAll("([!#(){}\\[\\].+\\-<>|])", "\\\\$1");
    }
    private void sendCategories(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Выберите категорию или добавьте новую!");

        // Формируем inline-клавиатуру
        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Добавляем существующие категории, если они есть
        if (!category.isEmpty()) {
            for (String cat : category.keySet()) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(doButton(cat, "category_" + cat));
                rows.add(row);
            }
        }

        // Добавляем кнопку для добавления новой категории
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(doButton("Хочу добавить новую категорию", "add_category"));
        row.add(doButton("\u27F3 Начать заново", "start"));
        rows.add(row);

        inlineKeyboard.setKeyboard(rows);
        message.setReplyMarkup(inlineKeyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private InlineKeyboardButton doButton(String text, String data) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(data);
        return button;
    }
    private void menuWithTwoButtons(long chatId, String text, String textButton1, String callButton1,
                                    String textButton2, String callButton2) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        // Формируем inline-клавиатуру
        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton totalAllBtn = new InlineKeyboardButton();
        totalAllBtn.setText(textButton1);
        totalAllBtn.setCallbackData(callButton1);
        row.add(totalAllBtn);

        InlineKeyboardButton totalByCategoryBtn = new InlineKeyboardButton();
        totalByCategoryBtn.setText(textButton2);
        totalByCategoryBtn.setCallbackData(callButton2);
        row.add(totalByCategoryBtn);

        rows.add(row);
        inlineKeyboard.setKeyboard(rows);
        message.setReplyMarkup(inlineKeyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();
        if (data.startsWith("category_")) {
            String selectedCategory = data.substring(9);
            pendingExpenseName.put(chatId, new Pair<>(normalizeText(selectedCategory), Optional.empty()));
            userStates.put(chatId, ConversationState.AWAITING_EXPENSE_ABSTRACT);
            sendText(chatId, "\u270F Дабавьте описание расхода:");
        } else if (data.equals("add_category")) {// Переход к добавлению новой категории
            userStates.put(chatId, ConversationState.AWAITING_NEW_CATEGORY);
            sendText(chatId, "\u270F Напишите название новой категории:");
        } else if (data.equals("by_period")) {// Обработка отчета за все время
            sendText(chatId, "Напишите _*бервую*_ дату в формате ДД.ММ.ГГГ:");
            userStates.put(chatId, ConversationState.AWAITING_DATE_START);
        } else if (data.equals("total_mounth")) {// Обработка отчета за текущий месяц
            sendExpenses = DataBaseManager.getCurrentMonthExpenses();
            sendExpenseReport(chatId, sendExpenses, "Результаты текущего месяца");
        } else if (data.equals("add_expense")) {//добавление расхода
            sendCategories(chatId);
        } else if (data.equals("results")) {//вывод результатов
            menuWithTwoButtons(chatId, "Выберите вид результатов!", "Другие результаты",
                    "by_period", "Результаты текущего месяца", "total_mounth");
        } else if (data.equals("start")) {
            menuWithTwoButtons(chatId,  invite, addExpense, "add_expense", resultText, "results");
        } else if (data.equals("yes_excel")) {
            boolean res = toExcel(chatId, false);
            if (!res) sendText(chatId, empty);
            menuWithTwoButtons(chatId,  invite, addExpense, "add_expense", resultText, "results");
        } else if (data.equals("no_excel")) {
            menuWithTwoButtons(chatId,  invite, addExpense, "add_expense", resultText, "results");
        } else if (data.equals("agree")) {
            LocalDateTime now = LocalDateTime.now();
            Timestamp timestamp = Timestamp.valueOf(now);
            db.addExpense(expenseName, expenseAbstraction, amount, timestamp, chatId);
            Expense expense = new Expense(expenseName, expenseAbstraction, amount, now, chatId);
            userExpenses.putIfAbsent(chatId, new ArrayList<>());
            userExpenses.get(chatId).add(expense);
            userStates.put(chatId, ConversationState.NONE);
            pendingExpenseName.remove(chatId);
            sendText(chatId, "\u2705 В категорию \"" + expenseName + "\" был добавлен расход с суммой " + amount + ".");
            menuWithTwoButtons(chatId,  invite, addExpense, "add_expense", resultText, "results");
        } else if (data.equals("no_agree")) {
            menuWithTwoButtons(chatId,  invite, addExpense, "add_expense", resultText, "results");
        } else if (data.equals("agree_del")) {
            userStates.put(chatId, ConversationState.NONE);
            db.deleteExpense(id);
            sendText(chatId, "\u2705 Из базы был удален расход с номером " + "\"" + id + "\"!");
            menuWithTwoButtons(chatId,  invite, addExpense, "add_expense", resultText, "results");
        } else if (data.equals("no_agree_del")) {
            menuWithTwoButtons(chatId,  invite, addExpense, "add_expense", resultText, "results");
        }
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    private boolean toExcel(long chatId, boolean info) {
        if (info) {
            try {
                ByteArrayInputStream inputStream = DataBaseManager.getExpensesFromDatabase();
                if (inputStream == null || inputStream.available() == 0) {
                    sendText(chatId, empty);
                    return false;
                }
                SendDocument sendDocument = new SendDocument();
                sendDocument.setChatId(chatId);
                sendDocument.setDocument(new InputFile(inputStream, "expenses.xlsx"));
                execute(sendDocument); // Отправляем файл
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return true;
        }
        if (sendExpenses == null || sendExpenses.isEmpty()) return false;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Expenses");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Категория");
            headerRow.createCell(1).setCellValue("Сумма");
            int rowNum = 1;
            for (Expense item : sendExpenses) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getName());
                String str = insertNewLineEveryFiveValues(item.getAbstraction());
                row.createCell(1).setCellValue(str);
                row.createCell(2).setCellValue(item.getAmount());
            }

            sheet.autoSizeColumn(0);
//            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);
            setAutoColumnWidth(sheet, 1);

            Row totalRow = sheet.createRow(++rowNum);
            totalRow.createCell(0).setCellValue("Всего:");
            double total = sendExpenses.stream().mapToDouble(Expense::getAmount).sum();
            totalRow.createCell(2).setCellValue(total);

            sheet.protectSheet("12345");
            workbook.write(out);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(out.toByteArray());
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId);
            sendDocument.setDocument(new InputFile(inputStream, "expenses.xlsx"));
            execute(sendDocument);

        } catch (IOException | TelegramApiException e) {
            e.printStackTrace();
        }

        return true;
    }
    private static void setAutoColumnWidth(Sheet sheet, int columnIndex) {
        int maxWidth = 0;

        // Определение максимальной ширины колонки на основе содержимого ячеек
        for (int rowNum = 0; rowNum < sheet.getPhysicalNumberOfRows(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row != null) {
                Cell cell = row.getCell(columnIndex);
                if (cell != null) {
                    String cellValue = cell.toString();
                    maxWidth = Math.max(maxWidth, cellValue.length());
                }
            }
        }

        // Устанавливаем ширину колонки, ограничив её максимальной длиной (255 символов)
        maxWidth = Math.min(maxWidth, 255);  // Ограничиваем до 255 символов
        sheet.setColumnWidth(columnIndex, maxWidth * 100);  // Устанавливаем ширину
    }

    public static String insertNewLineEveryFiveValues(String input) {
        // Разбиваем строку по запятой
        String[] values = input.split(",");
        StringBuilder result = new StringBuilder();

        // Преобразуем массив обратно в строку, добавляя \n после каждого пятого элемента
        for (int i = 0; i < values.length; i++) {
            result.append(values[i]);

            // Добавляем \n после каждого пятого элемента
            if ((i + 1) % 5 == 0 && i != values.length - 1) {
                result.append(",\n");
            } else {
                result.append(", ");
            }
        }

        return result.toString();
    }
    private void sendExpenseReport(long chatId, List<Expense> expenses, String header) {
        StringBuilder sb = new StringBuilder(header + ":\n\n");
        for (Expense exp : expenses) {
            sb.append(exp.getName()).append(": [").append(exp.getAbstraction()).append("]: ").append(exp.getAmount()).append("\n");
        }

        double total = expenses.stream().mapToDouble(Expense::getAmount).sum();
        sb.append("*Всего: ").append(total).append("*\n");
        sendText(chatId, sb.toString());
        menuWithTwoButtons(chatId, "Нужны ли результаты в формате Excel?", symbolAgree, "yes_excel",
                symbolNotAgree, "no_excel");
    }
    public void deleteMessage(Long chatId, Integer messageId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId.toString());
        deleteMessage.setMessageId(messageId);
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    public static String normalizeText(String text) {
        return text.replaceAll("[^\\p{L}\\p{N} ]", "") // Удаляем знаки препинания
                .toLowerCase(Locale.ROOT) // Приводим к нижнему регистру
                .trim(); // Убираем лишние пробелы
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    public static void main(String[] args) {
        DataBaseManager.initDatabase();
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new ExpenseBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
