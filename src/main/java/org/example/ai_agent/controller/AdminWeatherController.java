package org.example.ai_agent.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@RestController
@RequestMapping("/api/admin/weather")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://dduo.chat", "https://dduo.chat"}, allowCredentials = "true")
public class AdminWeatherController {

    private final WebClient webClient;

    public AdminWeatherController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @GetMapping(value = "/local", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> getLocalWeather(@RequestParam Double latitude,
                                                     @RequestParam Double longitude) {
        Mono<Map> forecastMono = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.open-meteo.com")
                        .path("/v1/forecast")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("current", "temperature_2m,weather_code")
                        .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min")
                        .queryParam("timezone", "auto")
                        .queryParam("forecast_days", 3)
                        .build())
                .retrieve()
                .bodyToMono(Map.class);

        Mono<String> cityMono = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("geocoding-api.open-meteo.com")
                        .path("/v1/reverse")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("language", "zh")
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractLocationName)
                .onErrorReturn("");

        return Mono.zip(cityMono, forecastMono)
                .map(tuple -> {
                    String locationName = tuple.getT1();
                    if (locationName == null || locationName.isBlank()) {
                        locationName = "当前位置";
                    }

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("city", locationName);
                    payload.put("forecast", tuple.getT2());
                    return payload;
                });
    }

    private String extractLocationName(Map payload) {
        Object results = payload.get("results");
        if (results instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            StringJoiner joiner = new StringJoiner(" ");
            appendIfPresent(joiner, first.get("city"));
            appendIfPresent(joiner, first.get("district"));
            appendIfPresent(joiner, first.get("name"));
            appendIfPresent(joiner, first.get("admin1"));
            appendIfPresent(joiner, first.get("country"));

            String value = joiner.toString().trim();
            if (!value.isBlank()) {
                return value;
            }
        }

        return "";
    }
    private void appendIfPresent(StringJoiner joiner, Object value) {
        if (value != null) {
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                joiner.add(text);
            }
        }
    }
}
