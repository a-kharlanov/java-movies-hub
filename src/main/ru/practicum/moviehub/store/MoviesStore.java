package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new LinkedHashMap<>();
    private int nextId = 1;

    public synchronized Movie add(String title, int year) {
        Movie movie = new Movie(nextId++, title, year);
        movies.put(movie.getId(), movie);
        return movie;
    }

    public synchronized List<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }

    public synchronized List<Movie> getByYear(int year) {
        List<Movie> result = new ArrayList<>();
        for (Movie movie : movies.values()) {
            if (movie.getYear() == year) {
                result.add(movie);
            }
        }
        return result;
    }

    public synchronized Optional<Movie> getById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    public synchronized boolean delete(int id) {
        return movies.remove(id) != null;
    }

    public synchronized void clear() {
        movies.clear();
        nextId = 1;
    }
}