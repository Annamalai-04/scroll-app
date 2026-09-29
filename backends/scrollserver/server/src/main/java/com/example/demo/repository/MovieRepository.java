package com.example.demo.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.demo.model.Movie;

public interface MovieRepository extends MongoRepository<Movie, String> {

    // Type-filtered results in id order. With normal Mongo ObjectId ids,
    // ascending id follows insertion/creation order.
    List<Movie> findByTypeIgnoreCaseOrderByIdAsc(String type);
}
