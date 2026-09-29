package com.example.demo.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.demo.model.Movie;
import com.example.demo.repository.MovieRepository;

@Service
public class VideoService {

    private final MovieRepository movieRepository;

    public VideoService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public List<Movie> getVideos(String type) {

        if (type == null || type.trim().isEmpty()) {

            return movieRepository.findAll(
                    Sort.by(Sort.Direction.ASC, "id")
            );
        }

        return movieRepository
                .findByTypeIgnoreCaseOrderByIdAsc(type.trim());
    }
}
