package com.example.tool;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class Tools {

    private final RestClient restClient;

    public Tools(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://wttr.in").build();
    }

    @Tool(description = "지역 이름을 받아서 현재 날씨를 조회합니다.", returnDirect = true)
    public String getWeather(@ToolParam(description = "지역 이름") String location) {

        String customFormat = "현재 %l의 날씨는 %c 상태이며, 기온은 %t, 체감 기온은 %f, 풍속은 %w, 습도는 %h, 강수량은 %p입니다";

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{location}")
                        .queryParam("format", customFormat)
                        .queryParam("lang", "ko")
                        .build(location))
                .retrieve()
                .body(String.class);
    }

    @Tool(description = "지역 이름을 받아 현재 3일간의 날씨와 천문 정보(달의 밝기, 달의 위상, 해/달의 뜨고 지는 시각) 를 조회합니다.")
    public WeatherResponse getWeatherDetails(@ToolParam(description = "지역 이름") String location) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{location}")
                        .queryParam("format", "j1") // json 출력으로 제공
                        .queryParam("lang", "ko")
                        .build(location))
                .retrieve()
                .body(WeatherResponse.class);       // JSON 응답을 즉시 WeatherResponse Record 객체로 맵핑
    }

// Record(DTO) 클래스

    public record WeatherResponse(
            @Schema(description = "일별(3일) 예보 정보 리스트")
            List<WeatherForecast> weather
    ) {
    }

    // 일별 예보 (hourly 제외)
    public record WeatherForecast(
            @Schema(description = "천문 정보(일출, 일몰 등)") List<Astronomy> astronomy,
            @Schema(description = "해당 날짜(yyyy-MM-dd)") String date,
            @Schema(description = "평균 기온(섭씨)") int avgtempC,
            @Schema(description = "평균 기온(화씨)") int avgtempF,
            @Schema(description = "최고 기온(섭씨)") int maxtempC,
            @Schema(description = "최고 기온(화씨)") int maxtempF,
            @Schema(description = "최저 기온(섭씨)") int mintempC,
            @Schema(description = "최저 기온(화씨)") int mintempF,
            @Schema(description = "일조 시간(시간 단위)") double sunHour,
            @Schema(description = "적설량(센티미터)") double totalSnow_cm,
            @Schema(description = "자외선 지수") int uvIndex
    ) {
    }

    // 천문 정보
    public record Astronomy(
            @Schema(description = "달 밝기(%)") int moon_illumination,
            @Schema(description = "달의 위상(예: Full Moon 등)") String moon_phase,
            @Schema(description = "달 뜨는 시각") String moonrise,
            @Schema(description = "달 지는 시각") String moonset,
            @Schema(description = "해 뜨는 시각") String sunrise,
            @Schema(description = "해 지는 시각") String sunset
    ) {
    }
}
