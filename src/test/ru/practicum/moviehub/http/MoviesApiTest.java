package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static final String CT_JSON = "application/json; charset=UTF-8";

    private static final Gson gson = new Gson();
    private static final MoviesStore store = new MoviesStore();
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @BeforeEach
    void clearStore() {
        store.clear();
    }

    private HttpResponse<String> send(HttpRequest req) throws Exception {
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder().uri(URI.create(BASE + path)).GET().build());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return send(HttpRequest.newBuilder().uri(URI.create(BASE + path)).DELETE().build());
    }

    private HttpResponse<String> post(String body, String contentType) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", contentType)
                .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build());
    }

    private HttpResponse<String> postJson(String body) throws Exception {
        return post(body, CT_JSON);
    }

    private void assertJsonContentType(HttpResponse<String> resp) {
        assertEquals(CT_JSON, resp.headers().firstValue("Content-Type").orElse(""),
                "Content-Type должен содержать формат данных и кодировку");
    }

    private JsonObject asObject(HttpResponse<String> resp) {
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private List<Movie> asMovies(HttpResponse<String> resp) {
        return gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());
    }

    private JsonArray asArray(HttpResponse<String> resp) {
        return JsonParser.parseString(resp.body()).getAsJsonArray();
    }

    private void assertValidationError(HttpResponse<String> resp) {
        assertEquals(422, resp.statusCode());
        assertJsonContentType(resp);
        JsonObject body = asObject(resp);
        assertTrue(body.has("error"));
        assertFalse(body.getAsJsonArray("details").isEmpty(), "details не должен быть пустым");
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = get("/movies");

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        assertJsonContentType(resp);
        assertEquals(0, asArray(resp).size());
    }

    @Test
    void getMovies_returnsPreviouslyAddedMovies() throws Exception {
        store.add("Матрица", 1999);
        store.add("Начало", 2010);

        HttpResponse<String> resp = get("/movies");

        assertEquals(200, resp.statusCode());
        List<Movie> movies = asMovies(resp);
        assertEquals(2, movies.size());
        assertEquals("Матрица", movies.get(0).getTitle());
        assertEquals(2010, movies.get(1).getYear());
    }

    @Test
    void postMovie_withValidData_returns201AndMovieWithId() throws Exception {
        HttpResponse<String> resp = postJson("{\"title\":\"Интерстеллар\",\"year\":2014}");

        assertEquals(201, resp.statusCode());
        assertJsonContentType(resp);
        JsonObject movie = asObject(resp);
        assertTrue(movie.get("id").getAsInt() > 0);
        assertEquals("Интерстеллар", movie.get("title").getAsString());
        assertEquals(2014, movie.get("year").getAsInt());
    }

    @Test
    void postMovie_acceptsBoundaryYears() throws Exception {
        assertEquals(201, postJson("{\"title\":\"Старое\",\"year\":1888}").statusCode());
        int nextYear = Year.now().getValue() + 1;
        assertEquals(201, postJson("{\"title\":\"Новое\",\"year\":" + nextYear + "}").statusCode());
    }

    @Test
    void postMovie_withEmptyTitle_returns422() throws Exception {
        assertValidationError(postJson("{\"title\":\"\",\"year\":2000}"));
        assertValidationError(postJson("{\"title\":\"   \",\"year\":2000}"));
        assertValidationError(postJson("{\"year\":2000}"));
    }

    @Test
    void postMovie_withTooLongTitle_returns422() throws Exception {
        String longTitle = "a".repeat(101);
        assertValidationError(postJson("{\"title\":\"" + longTitle + "\",\"year\":2000}"));
    }

    @Test
    void postMovie_withInvalidYear_returns422() throws Exception {
        assertValidationError(postJson("{\"title\":\"Фильм\",\"year\":1887}"));
        int tooFar = Year.now().getValue() + 2;
        assertValidationError(postJson("{\"title\":\"Фильм\",\"year\":" + tooFar + "}"));
        assertValidationError(postJson("{\"title\":\"Фильм\"}"));
    }

    @Test
    void postMovie_withWrongContentType_returns415() throws Exception {
        HttpResponse<String> resp = post("{\"title\":\"Фильм\",\"year\":2000}", "text/plain");

        assertEquals(415, resp.statusCode());
        assertJsonContentType(resp);
        assertTrue(asObject(resp).has("error"));
    }

    @Test
    void postMovie_withMalformedJson_returns400() throws Exception {
        HttpResponse<String> resp = postJson("{\"title\": ");

        assertEquals(400, resp.statusCode());
        assertJsonContentType(resp);
        assertTrue(asObject(resp).has("error"));
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        Movie saved = store.add("Побег из Шоушенка", 1994);

        HttpResponse<String> resp = get("/movies/" + saved.getId());

        assertEquals(200, resp.statusCode());
        assertJsonContentType(resp);
        assertEquals("Побег из Шоушенка", asObject(resp).get("title").getAsString());
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = get("/movies/999");

        assertEquals(404, resp.statusCode());
        assertJsonContentType(resp);
        assertEquals("Фильм не найден", asObject(resp).get("error").getAsString());
    }

    @Test
    void getMovieById_whenIdIsNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = get("/movies/abc");

        assertEquals(400, resp.statusCode());
        assertJsonContentType(resp);
        assertEquals("Некорректный ID", asObject(resp).get("error").getAsString());
    }

    @Test
    void deleteMovie_whenExists_returns204AndRemovesIt() throws Exception {
        Movie saved = store.add("Фильм", 2000);

        HttpResponse<String> resp = delete("/movies/" + saved.getId());

        assertEquals(204, resp.statusCode());
        assertEquals("", resp.body());
        assertEquals(404, get("/movies/" + saved.getId()).statusCode());
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = delete("/movies/999");

        assertEquals(404, resp.statusCode());
        assertTrue(asObject(resp).has("error"));
    }

    @Test
    void deleteMovie_whenIdIsNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = delete("/movies/abc");

        assertEquals(400, resp.statusCode());
        assertTrue(asObject(resp).has("error"));
    }

    @Test
    void getMoviesByYear_returnsOnlyMoviesOfThatYear() throws Exception {
        store.add("Матрица", 1999);
        store.add("Бойцовский клуб", 1999);
        store.add("Начало", 2010);

        HttpResponse<String> resp = get("/movies?year=1999");

        assertEquals(200, resp.statusCode());
        assertJsonContentType(resp);
        List<Movie> movies = asMovies(resp);
        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.getYear() == 1999));
    }

    @Test
    void getMoviesByYear_whenNoMatches_returnsEmptyArray() throws Exception {
        store.add("Матрица", 1999);

        HttpResponse<String> resp = get("/movies?year=1950");

        assertEquals(200, resp.statusCode());
        assertEquals(0, asArray(resp).size());
    }

    @Test
    void getMoviesByYear_whenYearIsNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = get("/movies?year=abc");

        assertEquals(400, resp.statusCode());
        assertJsonContentType(resp);
        assertTrue(asObject(resp).has("error"));
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpResponse<String> onCollection = send(HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .PUT(BodyPublishers.ofString("{}"))
                .build());
        assertEquals(405, onCollection.statusCode());

        HttpResponse<String> onItem = send(HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .PUT(BodyPublishers.ofString("{}"))
                .build());
        assertEquals(405, onItem.statusCode());
        assertJsonContentType(onItem);
        assertTrue(asObject(onItem).has("error"));
    }
}