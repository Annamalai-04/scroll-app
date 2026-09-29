package com.example.demo.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.dto.HistoryRequest;
import com.example.demo.model.History;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;

@Service
public class HistoryService {

    private final UserRepository userRepository;

    public HistoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void saveHistory(
            String userId,
            HistoryRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        List<History> historyList = user.getHistory();

        if (historyList == null) {
            historyList = new ArrayList<>();
            user.setHistory(historyList);
        }

        History existing = null;

        for (History history : historyList) {

            if (history.getVideoId() != null
                    && history.getVideoId()
                    .equals(request.getVideoId())) {

                existing = history;
                break;
            }
        }

        if (existing == null) {

            existing = new History();
            existing.setVideoId(request.getVideoId());
            historyList.add(existing);
        }

        existing.setDate(LocalDateTime.now());
        existing.setPercent(
                Math.max(
                        0,
                        Math.min(
                                100,
                                request.getPercent()
                        )
                )
        );
        existing.setWatched(
                request.getWatched() == null
                        ? "0:00"
                        : request.getWatched()
        );
        existing.setLength(
                request.getLength() == null
                        ? "0:00"
                        : request.getLength()
        );

        userRepository.save(user);
    }

    public List<History> getHistory(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        if (user.getHistory() == null) {
            return new ArrayList<>();
        }

        return user.getHistory();
    }
    
    public void deleteHistory(
            String userId,
            String videoId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        if (user.getHistory() != null) {

            user.getHistory().removeIf(history ->
                    videoId.equals(history.getVideoId())
            );
        }

        userRepository.save(user);
    }
}
