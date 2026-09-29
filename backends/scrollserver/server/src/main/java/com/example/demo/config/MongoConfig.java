package com.example.demo.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class MongoConfig {

    private static final String MONGO_URI =
            System.getenv().getOrDefault(
                    "MONGO_URI",
                    "mongodb://localhost:27017"
            );

    private static final String DATABASE_NAME =
    		  System.getenv().getOrDefault(
    	                "MONGO_DATABASE",
    	                "mydbase"
    	        );

    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(MONGO_URI);
    }

    @Bean
    public SimpleMongoClientDatabaseFactory mongoDatabaseFactory(
            MongoClient mongoClient) {

        return new SimpleMongoClientDatabaseFactory(
                mongoClient,
                DATABASE_NAME
        );
    }

    @Bean
    public MongoTemplate mongoTemplate(
            SimpleMongoClientDatabaseFactory mongoDatabaseFactory) {

        return new MongoTemplate(mongoDatabaseFactory);
    }
}