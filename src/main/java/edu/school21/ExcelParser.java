package edu.school21;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Set;
import java.util.TreeSet;

public class ExcelParser {
    public static String findRowsByTextAndDate(String url, String searchText, boolean isToday) {
        StringBuilder result = new StringBuilder();
        String todayDate = getTargetDate(isToday);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(request);
                 InputStream excelStream = response.getEntity().getContent()) {
                Workbook workbook = new XSSFWorkbook(excelStream); // Для формата .xlsx
                Sheet sheet = workbook.getSheetAt(0);

                // Берем заголовки из первой строки
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    return "\uD83E\uDD37 В файле отсутствуют заголовки";
                }

                // Проходим по всем строкам, начиная со второй (индекс 1)
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    boolean foundText = false;
                    boolean foundDate = false;

                    // Проверяем первый столбец (например, город) на наличие искомого слова
                    Cell cell0 = row.getCell(0);
                    if (cell0 != null && cell0.getCellType() == CellType.STRING
                            && cell0.getStringCellValue().contains(searchText)) {
                        foundText = true;
                    }

                    // Проверяем второй столбец на совпадение с сегодняшней датой
                    Cell cell1 = row.getCell(1);
                    if (cell1 != null) {
                        String cellDate = null;
                        if (cell1.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell1)) {
                            cellDate = new SimpleDateFormat("dd.MM.yyyy").format(cell1.getDateCellValue());
                        } else if (cell1.getCellType() == CellType.STRING) {
                            cellDate = cell1.getStringCellValue();
                        }
                        if (cellDate != null && cellDate.equals(todayDate)) {
                            foundDate = true;
                        }
                    }

                    // Если оба условия выполнены, форматируем строку с учетом заголовков
                    if (foundText && foundDate) {
                        result.append(formatRow(row, headerRow)).append("\n");
                    }
                }

                if (result.length() == 0) {
                    String message = "Информация не найдена: https://dumrt.ru/ru/help-info/prayertime/?t=170225";
                    String escapedMessage = escapeText(message);
                    result.append(escapedMessage);
                }
            }
        } catch (Exception e) {
            result.append("Ошибка обработки файла").append(e.getMessage());
        }

        return result.toString();
    }
    private static String escapeText(String text) {
        return text.replace("=", "\\=").replace("&", "\\&").replace("?", "\\?");
    }
    private static String getTargetDate(boolean isToday) {
        Calendar calendar = Calendar.getInstance();
        if (!isToday) {
            calendar.add(Calendar.DAY_OF_YEAR, 1); // Добавляем 1 день, если нужен завтрашний
        }
        return new SimpleDateFormat("dd.MM.yyyy").format(calendar.getTime());
    }

    private static String formatRow(Row row, Row headerRow) {
        StringBuilder rowText = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");

        int lastCellIndex = headerRow.getLastCellNum(); // Определяем число столбцов по заголовку
        for (int i = 0; i < lastCellIndex; i++) {
            // Получаем название столбца из заголовка
            String headerName = "";
            Cell headerCell = headerRow.getCell(i);
            if (headerCell != null && headerCell.getCellType() == CellType.STRING) {
                String tmp = headerCell.getStringCellValue();
                headerName = tmp.length() < 19 ? tmp : tmp.substring(0, 19) + "...";
//                headerName = headerCell.getStringCellValue();

            }

            // Обработка значения текущей строки
            String cellOutput = "";
            Cell cell = row.getCell(i);
            if (cell != null) {
                if (i == 0) {
                    // Первый столбец – выводим значение как есть
                    if (cell.getCellType() == CellType.STRING) {
                        cellOutput = cell.getStringCellValue();
                    } else {
                        cellOutput = cell.toString();
                    }
                } else if (i == 1) {
                    // Второй столбец – выводим только дату
                    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                        cellOutput = dateFormat.format(cell.getDateCellValue());
                    } else if (cell.getCellType() == CellType.STRING) {
                        cellOutput = cell.getStringCellValue();
                    } else {
                        cellOutput = cell.toString();
                    }
                } else {
                    // Остальные столбцы – выводим только время
                    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                        cellOutput = timeFormat.format(cell.getDateCellValue());
                    } else if (cell.getCellType() == CellType.STRING) {
                        cellOutput = cell.getStringCellValue();
                    } else {
                        cellOutput = cell.toString();
                    }
                }
            }
            if (headerName.length() > 0)
            rowText.append(headerName).append(":\t\t").append("*").append(cellOutput).append("*");
            if (i < lastCellIndex - 1) {
                rowText.append("\t").append("\n");
            }
        }
        return rowText.toString().trim();
    }
}