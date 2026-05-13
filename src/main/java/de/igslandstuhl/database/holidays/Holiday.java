package de.igslandstuhl.database.holidays;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.igslandstuhl.database.api.SchoolYear;

public final class Holiday {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String API_URL = "https://www.mehr-schulferien.de/api/v2.1/schools/66849-integrierte-gesamtschule-am-na/periods";
    private static final String SUMMER_HOLIDAY_ID = "Sommer";
    private static final Logger LOGGER = LoggerFactory.getLogger(Holiday.class);

    private final int id;
    private final String name;
    private final Instant startsOn, endsOn;
    private final int locationId;
    private final boolean publicHoliday, schoolVacation;

    public Holiday(int id, String name, Instant startsOn, Instant endsOn, int locationId, boolean publicHoliday,
            boolean schoolVacation) {
        this.id = id;
        this.name = name;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.locationId = locationId;
        this.publicHoliday = publicHoliday;
        this.schoolVacation = schoolVacation;
    }

    public int getId() {
        return id;
    }
    public Instant getStart() {
        return startsOn;
    }
    public Instant getEnd() {
        return endsOn;
    }
    public int getLocationId() {
        return locationId;
    }
    public boolean isPublicHoliday() {
        return publicHoliday;
    }
    public boolean isSchoolVacation() {
        return schoolVacation;
    }
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return "Holiday [id=" + id + ", name=" + name + ", startsOn=" + startsOn + ", endsOn=" + endsOn
                + ", locationId=" + locationId + ", publicHoliday=" + publicHoliday + ", schoolVacation="
                + schoolVacation + "]";
    }

    private static int readInt(Map<String, Object> map, String key) {
        return ((Number)map.get(key)).intValue();
    }
    private static boolean readBool(Map<String, Object> map, String key) {
        return (Boolean) map.get(key);
    }
    private static Instant readInstantStart(Map<String, Object> map, String key) {
        String date = (String) map.get(key);
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
    private static Instant readInstantEnd(Map<String, Object> map, String key) {
        String date = (String) map.get(key);
        return LocalDate.parse(date).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
    }
    private static String readString(Map<String, Object> map, String key) {
        return (String) map.get(key);
    }

    public static Holiday fromMap(Map<String, Object> jsonMap) {
        int id = readInt(jsonMap, "id");
        String name = readString(jsonMap, "name");
        Instant startsOn = readInstantStart(jsonMap, "starts_on");
        Instant endsOn = readInstantEnd(jsonMap, "ends_on");
        int locationId = readInt(jsonMap, "location_id");
        boolean isPublicHoliday = readBool(jsonMap, "is_public_holiday");
        boolean isSchoolVacation = readBool(jsonMap, "is_school_vacation");

        return new Holiday(id, name, startsOn, endsOn, locationId, isPublicHoliday, isSchoolVacation);

    }
    public static Holiday fromJson(String json) {
        Gson gson = new Gson();
        java.lang.reflect.Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> jsonMap = gson.fromJson(json, mapType);
        return fromMap(jsonMap);
    }

    public static List<Holiday> readList(String json) {
        Gson gson = new Gson();
        java.lang.reflect.Type listType = new TypeToken<List<Map<String,Object>>>(){}.getType();
        List<Map<String,Object>> list = gson.fromJson(json, listType);
        return list.stream().map(Holiday::fromMap).toList();
    }

    public static Holiday[] holidaysInterval(Instant start, Instant end) {
        String startIso = start.atZone(ZoneId.of("UTC")).toLocalDate().format(DateTimeFormatter.ISO_DATE);
        String endIso = end.atZone(ZoneId.of("UTC")).toLocalDate().format(DateTimeFormatter.ISO_DATE);
        try {
            URL url = new URL(API_URL + "?start_date=" + startIso + "&end_date=" + endIso);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url.toURI())
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) throw new IllegalStateException("Server responded with Status code " + response.statusCode());

            String body = response.body();
            String list = "[" + body.split("\\[")[1].split("\\]")[0] + "]";
            List<Holiday> holidays = readList(list);
            Holiday[] holidayArr = new Holiday[holidays.size()];
            return holidays.toArray(holidayArr);
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        }
    }

    public static Holiday getSummerHoliday(Instant timeIntervalStart, Instant timeIntervalEnd) {
        Holiday[] lastYearHolidays = holidaysInterval(timeIntervalStart, timeIntervalEnd);
        return Arrays.stream(lastYearHolidays)
        .filter((holiday) -> (holiday.getName().equals(SUMMER_HOLIDAY_ID)))
        .sorted((h1, h2) -> h1.getEnd().compareTo(h2.getEnd()))
        .findFirst().get();
    }
    public static Holiday getLastSummerHoliday() {
        Instant now = Instant.now();
        Instant yearAgo = now.minusSeconds(31536000);
        return getSummerHoliday(yearAgo, now);
    }
    public static Holiday getNextSummerHoliday() {
        Instant now = Instant.now();
        Instant inAYear = now.plusSeconds(31536000);
        return getSummerHoliday(now, inAYear);
    }
    public static int getTotalWeeks() {
        return (int) SchoolWeek.getAll(getLastSummerHoliday().getEnd(), getNextSummerHoliday().getStart(), ZoneId.of("UTC")).stream().filter(SchoolWeek::noSchoolUTC).count();
    }
    public static int getActualWeek() {
        return (int) SchoolWeek.getAll(getLastSummerHoliday().getEnd(), Instant.now(), ZoneId.of("UTC")).stream().filter(SchoolWeek::noSchoolUTC).count();
    }
    public static void setupCurrentSchoolYear() throws SQLException {
        LOGGER.info("Trying to set up current school year...");
        String name = getLastSummerHoliday().getEnd().toString().substring(0, 4) + "/" + getNextSummerHoliday().getStart().toString().substring(0, 4);
        int totalWeeks = getTotalWeeks();
        int actualWeek = getActualWeek();
        SchoolYear.addSchoolYear(name, totalWeeks, actualWeek);
    }
}
