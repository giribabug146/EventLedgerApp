package com.example.gateway.controller;

import com.example.gateway.dto.AccountBalanceResponse;
import com.example.gateway.dto.EventRequest;
import com.example.gateway.dto.EventResponse;
import com.example.gateway.service.EventService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse receive(@Valid @RequestBody EventRequest request) {
        return eventService.receive(request);
    }

    @GetMapping("/events/{eventId}")
    public EventResponse getEvent(@PathVariable String eventId) {
        return eventService.getEvent(eventId);
    }

    @GetMapping("/events")
    public List<EventResponse> getEvents(@RequestParam("account") String accountId) {
        return eventService.getEventsForAccount(accountId);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public AccountBalanceResponse getBalance(@PathVariable String accountId) {
        return eventService.getBalance(accountId);
    }
}
