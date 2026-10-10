package ru.practicum.moviehub.http;

import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MoviesHandler extends BaseHttpHandler {
    private static final int MIN_YEAR = 1888;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final Logger LOG = Logger.getLogger(MoviesHandler.class.getName());

    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String[] parts = ex.getRequestURI().getPath().split("/");

            if (parts.length == 2) {
                handleCollection(ex);
            } else if (parts.length == 3) {
                handleItem(ex, parts[2]);
            } else {
                sendError(ex, 404, new ErrorResponse("Ресурс не найден"));
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Ошибка при обработке запроса "
                    + ex.getRequestMethod() + " " + ex.getRequestURI(), e);
            sendError(ex, 500, new ErrorResponse("Внутренняя ошибка сервера"));
        }
    }

    private void handleCollection(HttpExchange ex) throws IOException {
        switch (ex.getRequestMethod().toUpperCase()) {
            case "GET":
                handleGetAll(ex);
                break;
            case "POST":
                handlePost(ex);
                break;
            default:
                sendMethodNotAllowed(ex, "GET, POST");
        }
    }

    private void handleGetAll(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            sendObject(ex, 200, store.getAll());
            return;
        }

        Integer year = parseYearParam(query);
        if (year == null) {
            sendError(ex, 400, new ErrorResponse("Некорректный параметр запроса — 'year'"));
            return;
        }
        sendObject(ex, 200, store.getByYear(year));
    }

    private Integer parseYearParam(String query) {
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals("year") && kv.length == 2) {
                try {
                    return Integer.parseInt(kv[1]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private void handlePost(HttpExchange ex) throws IOException {
        if (!isJsonContentType(ex)) {
            sendError(ex, 415, new ErrorResponse("Неподдерживаемый тип содержимого"));
            return;
        }

        Movie incoming;
        try {
            incoming = gson.fromJson(readBody(ex), Movie.class);
        } catch (JsonSyntaxException e) {
            sendError(ex, 400, new ErrorResponse("Некорректный JSON"));
            return;
        }
        if (incoming == null) { // пустое тело или "null"
            sendError(ex, 400, new ErrorResponse("Некорректный JSON"));
            return;
        }

        List<String> problems = validate(incoming);
        if (!problems.isEmpty()) {
            sendError(ex, 422, new ErrorResponse("Ошибка валидации", problems));
            return;
        }

        Movie created = store.add(incoming.getTitle(), incoming.getYear());
        sendObject(ex, 201, created);
    }

    private boolean isJsonContentType(HttpExchange ex) {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null) {
            return false;
        }
        String mediaType = contentType.split(";")[0].trim();
        return mediaType.equalsIgnoreCase("application/json");
    }

    private List<String> validate(Movie movie) {
        List<String> problems = new ArrayList<>();

        String title = movie.getTitle();
        if (title == null || title.isBlank()) {
            problems.add("название не должно быть пустым");
        } else if (title.length() > MAX_TITLE_LENGTH) {
            problems.add("название не должно быть длиннее " + MAX_TITLE_LENGTH + " символов");
        }

        int maxYear = Year.now().getValue() + 1;
        Integer year = movie.getYear();
        if (year == null || year < MIN_YEAR || year > maxYear) {
            problems.add("год должен быть между " + MIN_YEAR + " и " + maxYear);
        }
        return problems;
    }

    private void handleItem(HttpExchange ex, String rawId) throws IOException {
        String method = ex.getRequestMethod().toUpperCase();
        if (!method.equals("GET") && !method.equals("DELETE")) {
            sendMethodNotAllowed(ex, "GET, DELETE");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(rawId);
        } catch (NumberFormatException e) {
            sendError(ex, 400, new ErrorResponse("Некорректный ID"));
            return;
        }

        if (method.equals("GET")) {
            Optional<Movie> movie = store.getById(id);
            if (movie.isPresent()) {
                sendObject(ex, 200, movie.get());
            } else {
                sendError(ex, 404, new ErrorResponse("Фильм не найден"));
            }
        } else {
            if (store.delete(id)) {
                sendNoContent(ex);
            } else {
                sendError(ex, 404, new ErrorResponse("Фильм не найден"));
            }
        }
    }

    private void sendMethodNotAllowed(HttpExchange ex, String allowed) throws IOException {
        ex.getResponseHeaders().set("Allow", allowed);
        sendError(ex, 405, new ErrorResponse("Метод не поддерживается"));
    }
}
