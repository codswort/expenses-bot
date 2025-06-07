package edu.school21;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class DataBaseManager {
    private static HikariDataSource dataSource = null;
    public static Connection connect() throws SQLException {
        if (dataSource == null) {
            initDataSource();
        }
        return dataSource.getConnection();
    }
    private static void initDataSource() {
        if (dataSource == null) {
            String home = System.getProperty("user.home");
            String folderPath = home + "/.telegram-bot";
            String dbPath = folderPath + "/database.db";

            // Создаём папку, если её нет
            File folder = new File(folderPath);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbPath);
            config.setMaximumPoolSize(10);
            dataSource = new HikariDataSource(config);
        }
    }

    public static void initDatabase() {
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            String createTableSQL = Query.CREATE_TABLE.getQuery();
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void addExpense(String name, String abstraction, double price, Timestamp date, long owner_id) {
        String sql = Query.INSERT.getQuery();
        try (Connection conn = connect();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, abstraction);
            pstmt.setDouble(3, price);
            pstmt.setTimestamp(4, date);
            pstmt.setLong(5, owner_id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public static void deleteExpense(long id) {
        String sql = "DELETE FROM expenses WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public static Expense findExpenseById(long id) {
        String sql = "SELECT * FROM expenses WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new Expense(
                        rs.getString("name"),
                        rs.getString("abstraction"),
                        rs.getDouble("price"),
                        rs.getTimestamp("date").toLocalDateTime(),
                        rs.getLong("owner_id")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // если запись не найдена
    }


    public static List<Expense> getCurrentMonthExpenses() {
        List<Expense> expenses = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String sql = Query.GET_MOUNTH_EXPENSES.getQuery();
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            LocalDate firstDayOfMonth = now.toLocalDate().withDayOfMonth(1); // Первый день текущего месяца
            LocalDateTime startOfMonth = firstDayOfMonth.atStartOfDay(); // Преобразуем в LocalDateTime с началом дня

            LocalDateTime startOfNextMonth = now.plusMonths(1).toLocalDate().withDayOfMonth(1).atStartOfDay(); // Первый день следующего месяца

            pstmt.setTimestamp(1, Timestamp.valueOf(startOfMonth));  // Первый день текущего месяца
            pstmt.setTimestamp(2, Timestamp.valueOf(startOfNextMonth));

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                expenses.add(new Expense(
                        rs.getString("name"),
                        rs.getString("abstraction"),
//                        "",
                        rs.getDouble("total_price"),
                        now,  // Мы используем текущую дату, так как это расходы за текущий месяц
                        0L // owner_id может быть нулевым, так как мы не фильтруем по пользователю
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return expenses;
    }

    public static List<Expense> getExpensesForPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        List<Expense> expenses = new ArrayList<>();
        String sql = Query.GET_BY_PERIOD_EXPENSES.getQuery();
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // Устанавливаем параметры для startDate и endDate
            pstmt.setTimestamp(1, Timestamp.valueOf(startDate));  // Начало периода
            pstmt.setTimestamp(2, Timestamp.valueOf(endDate)); // Конец периода
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                expenses.add(new Expense(
                        rs.getString("name"),
                        rs.getString("abstraction"),
//                        "",
                        rs.getDouble("total_price"),
                        startDate,  // Используем начало периода
                        0L
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return expenses;
    }
    public static ByteArrayInputStream getExpensesFromDatabase() {
        String sql = Query.GET_ALL_FROM_BD_TO_EXCEL.getQuery();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("allDataBase");

        try (Connection conn = connect(); // Предполагается, что у вас есть метод для подключения
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery();  // Просто выполняем запрос
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            ResultSetMetaData rsMetaData = rs.getMetaData();
            int columnCount = rsMetaData.getColumnCount();

            // Создаем строку заголовков
            Row headerRow = sheet.createRow(0);
            for (int i = 1; i <= columnCount; i++) {
                headerRow.createCell(i - 1).setCellValue(rsMetaData.getColumnName(i));
                sheet.autoSizeColumn(i-1);
            }

            // Заполнение данных в Excel
            int rowNum = 1;
            boolean hasData = false;

            while (rs.next()) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 1; i <= columnCount; i++) {
                    row.createCell(i - 1).setCellValue(rs.getString(i));
                }
                hasData = true; // Данные есть
            }

            if (!hasData) {
                return null;  // Если нет данных, возвращаем null
            }
            for (int i = 0; i < 5; i++) sheet.autoSizeColumn(i);

            sheet.protectSheet("12345");  // Защищаем лист

            workbook.write(out);  // Записываем данные в ByteArrayOutputStream
            return new ByteArrayInputStream(out.toByteArray());  // Возвращаем поток данных

        } catch (SQLException | IOException e) {
            e.printStackTrace();
            return null;  // Если ошибка, возвращаем null
        }
    }
    public static Map<String, Double> getCategories() {
        Map<String, Double> categories = new TreeMap<>();
        String sql = Query.GET_CATEGORIES.getQuery();

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String categoryName = rs.getString("name");
                double totalPrice = rs.getDouble("total_price");
                categories.put(categoryName, totalPrice); // Добавляем в Map
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return categories;
    }
}
