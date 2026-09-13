package com.orangetv.controller.live;

import com.orangetv.config.SkipWrapper;
import com.orangetv.service.LiveStreamService;
import com.orangetv.util.LiveAddress;
import com.orangetv.util.LiveHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LivePlaybackController {
    private final LiveStreamService streams;

    @GetMapping("/resolve")
    public Map<String, Object> resolve(@RequestParam String url,
            @RequestParam(required = false) String headers,
            @RequestParam(required = false) String ua) {
        return streams.resolve(LiveAddress.decodeProxyUrl(url), headers(headers, ua));
    }

    @GetMapping("/stream")
    @SkipWrapper
    public void stream(@RequestParam String url,
            @RequestParam(required = false) String headers,
            @RequestParam(required = false) String ua,
            @RequestParam(defaultValue = "false") boolean manifest,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        streams.proxy(LiveAddress.decodeProxyUrl(url), headers(headers, ua), manifest, request, response);
    }

    private Map<String, String> headers(String encoded, String ua) {
        Map<String, String> values = LiveHeaders.fromEncoded(encoded);
        if (ua != null && !values.containsKey("User-Agent")) LiveHeaders.put(values, "ua", ua);
        return values;
    }
}
