package com.example.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.HistoryRequest;
import com.example.demo.model.History;
import com.example.demo.service.HistoryService;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @PostMapping
    public ResponseEntity<String> saveHistory(
            @RequestBody HistoryRequest request,
            Authentication authentication) {

        historyService.saveHistory(
                authentication.getName(),
                request
        );

        return ResponseEntity.ok("History saved");
    }

    @GetMapping
    public ResponseEntity<List<History>> getHistory(
            Authentication authentication) {

        return ResponseEntity.ok(
                historyService.getHistory(
                        authentication.getName()
                )
        );
    }
    
    @DeleteMapping("/{videoId}")
    public ResponseEntity<String> deleteHistory(
            @PathVariable("videoId") String videoId,
            Authentication authentication) {

        historyService.deleteHistory(
                authentication.getName(),
                videoId
        );

        return ResponseEntity.ok("History deleted");
    }
}
