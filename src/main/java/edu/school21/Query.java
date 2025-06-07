package edu.school21;

public enum Query {
    CREATE_TABLE("CREATE TABLE IF NOT EXISTS expenses (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT, " +
            "abstraction TEXT, " +
            "price REAL, " +
            "date TIMESTAMP, " +
            "owner_id BIGINT)"),
    INSERT("INSERT INTO expenses (name, abstraction, price, date, owner_id) VALUES (?, ?, ?, ?, ?)"),
    GET_MOUNTH_EXPENSES("SELECT LOWER(name) AS name, " +
        "REPLACE(GROUP_CONCAT(DISTINCT abstraction), ',', ',') AS abstraction, " +  // Убираем второй аргумент
        "SUM(price) AS total_price " +
        "FROM expenses WHERE date >= ? AND date < ? GROUP BY LOWER(name)"),

    GET_BY_PERIOD_EXPENSES("SELECT LOWER(name) AS name, " +
            "REPLACE(GROUP_CONCAT(DISTINCT abstraction), ',', ',') AS abstraction, " +  // Убираем второй аргумент
            "SUM(price) AS total_price " +
            "FROM expenses WHERE date >= ? AND date < ? GROUP BY LOWER(name)"),
    GET_ALL_FROM_BD_TO_EXCEL("SELECT \n" +
            "    id, \n" +
            "    name AS категория, \n" +
            "    abstraction AS информация, \n" +
            "    price AS стоимость, \n" +
            "    strftime('%d.%m.%Y %H:%M:%S', datetime(date / 1000, 'unixepoch', '+3 hours')) AS время, \n" +
            "   owner_id AS id автора расхода\n" +
            "FROM expenses;\n"),
    GET_CATEGORIES("SELECT LOWER(name) AS name, SUM(price) AS total_price FROM expenses " +
            "GROUP BY LOWER(name)");

    private final String QUERY;

    Query(String QUERY) {
        this.QUERY = QUERY;
    }

    public String getQuery() {
        return QUERY;
    }
}

