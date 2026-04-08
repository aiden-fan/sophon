package com.sophon.client.cli;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

final class HttpSupport {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private HttpSupport() {}

    static String getJson(String url, String bearerToken) throws IOException {
        Request.Builder b = new Request.Builder().url(url).get();
        if (bearerToken != null && !bearerToken.isBlank()) {
            b.header("Authorization", "Bearer " + bearerToken.trim());
        }
        Request request = b.build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            if (response.body() == null) {
                throw new IOException("empty response body");
            }
            return response.body().string();
        }
    }

    static String getJsonOrNullOn404(String url, String bearerToken) throws IOException {
        Request.Builder b = new Request.Builder().url(url).get();
        if (bearerToken != null && !bearerToken.isBlank()) {
            b.header("Authorization", "Bearer " + bearerToken.trim());
        }
        Request request = b.build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.code() == 404) {
                return null;
            }
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            if (response.body() == null) {
                throw new IOException("empty response body");
            }
            return response.body().string();
        }
    }

    static String postJson(String url, String jsonBody, String bearerToken) throws IOException {
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request.Builder b = new Request.Builder().url(url).post(body);
        if (bearerToken != null && !bearerToken.isBlank()) {
            b.header("Authorization", "Bearer " + bearerToken.trim());
        }
        Request request = b.build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            if (response.body() == null) {
                throw new IOException("empty response body");
            }
            return response.body().string();
        }
    }

    static void postSse(String url, String jsonBody, String bearerToken, Consumer<String> onLine) throws IOException {
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request.Builder b = new Request.Builder()
                .url(url)
                .post(body)
                .header("Accept", "text/event-stream");
        if (bearerToken != null && !bearerToken.isBlank()) {
            b.header("Authorization", "Bearer " + bearerToken.trim());
        }
        Request request = b.build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            ResponseBody rb = response.body();
            if (rb == null) {
                throw new IOException("empty response body");
            }
            var source = rb.source();
            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (line == null) {
                    break;
                }
                onLine.accept(line);
            }
        }
    }
}
