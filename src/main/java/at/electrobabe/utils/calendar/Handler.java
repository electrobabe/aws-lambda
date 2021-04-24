package at.electrobabe.utils.calendar;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.GetAccountSettingsRequest;
import software.amazon.awssdk.services.lambda.model.GetAccountSettingsResponse;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Lambda Handler
 * <p>
 * required GET params: WEBCAL_URL and DATE
 * <p>
 * https://calenderutils.electrobabe.at/
 */
@Slf4j
public class Handler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final LambdaAsyncClient lambdaClient = LambdaAsyncClient.create();

    private static final String TEMPLATE = "/index.html";
    private static final String BODY_PLACEHOLDER = "BODY_PLACEHOLDER";
    private static final String DEBUG_PLACEHOLDER = "DEBUG_PLACEHOLDER";
    private static final String HTML_BACKUP = "<!DOCTYPE html>" +
            "<html>\n" +
            "  <head><title>CalenderUtils</title><meta charset=\"UTF-8\"></head>\n" +
            "  <body>\n" +
            "    <h1>CalenderUtils</h1>\n" +
            "    <h2>Result</h2><p>" + BODY_PLACEHOLDER + "</p>\n" +
            "    <h2>Debug</h2><pre><code>" + DEBUG_PLACEHOLDER + "</code></pre></body>\n" +
            "</html>";

    static final String PARAM_WEBCAL_URL = "WEBCAL_URL";
    static final String PARAM_DATE = "DATE";

    private static final String PARAM_DEBUG = "debug";


    public Handler() {
        log.info("Handler constructor");
        try {
            // test getting settings
            CompletableFuture<GetAccountSettingsResponse> accountSettings = lambdaClient.getAccountSettings(GetAccountSettingsRequest.builder().build());
            GetAccountSettingsResponse settings = accountSettings.get();

            // settings: GetAccountSettingsResponse(AccountLimit=AccountLimit(TotalCodeSize=80530636800, CodeSizeUnzipped=262144000, CodeSizeZipped=52428800, ConcurrentExecutions=1000, UnreservedConcurrentExecutions=1000), AccountUsage=AccountUsage(TotalCodeSize=29575290, FunctionCount=2))
            log.debug("settings: {}", settings);

        } catch (Exception e) {
            log.warn("error setting up handler", e);
        }
    }

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {
        log.debug("event {}", gson.toJson(event));
        log.debug("context: {}", gson.toJson(context));

        String debug = "";
        if (event == null || event.getQueryStringParameters() == null) {
            String noParamsMsg = "Please enter an ICS / Webcal URL and pick a date";
            return response(noParamsMsg, HttpURLConnection.HTTP_NOT_ACCEPTABLE, debug);

        } else if (event.getQueryStringParameters().containsKey(PARAM_DEBUG)) {
            debug = "event: " + gson.toJson(event) + ", \ncontext: " + gson.toJson(context);
            log.info("debug: {}", debug);
        }

        if (event.getQueryStringParameters().containsKey(PARAM_WEBCAL_URL) && event.getQueryStringParameters().containsKey(PARAM_DATE)) {
            String text = getEvents(event, debug);

            return response(text, HttpURLConnection.HTTP_ACCEPTED, debug);

        } else {
            String noParamsMsg = "Please enter an ICS / Webcal URL and pick a date";
            return response(noParamsMsg, HttpURLConnection.HTTP_NOT_ACCEPTABLE, debug);
        }
    }


    private String getEvents(APIGatewayV2WebSocketEvent req, String debug) {
        String webcalUrl = req.getQueryStringParameters().get(PARAM_WEBCAL_URL);
        log.info("webcalUrl: {}", webcalUrl);

        String date = req.getQueryStringParameters().get(PARAM_DATE);
        log.info("date: {}", date);

        String events = "no events found!";
        try {
            log.debug("getting events as list");
            String eventsAsList = CalendarUtils.getCalendarEntriesForDay(date, webcalUrl);

            log.debug("eventsAsList: {}", eventsAsList);

            events = Arrays.stream(eventsAsList.split("\n")).map(s -> "<li>" + s + "</li>").collect(Collectors.joining("", "<ol>", "</ol>"));
            log.debug("formattedEvents {}", events);

        } catch (Exception e) {
            log.error("error getting events", e);
            debug += "<br/><b>ERROR: " + Arrays.toString(e.getStackTrace()) + "</b>";
        }

        log.debug("debug {}", debug);
        log.info("events {}", events);


        return String.format("<ul><li>webcalUrl: %s</li><li>date: %s</li><li>events: %s</li></ul>", webcalUrl, date, events);
    }

    private APIGatewayV2WebSocketResponse response(String ret, int httpStatusCode, String debug) {
        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setIsBase64Encoded(false);
        response.setStatusCode(httpStatusCode);
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html");
        response.setHeaders(headers);

        String template = getHTMLTemplate();

        response.setBody(template.replace(BODY_PLACEHOLDER, ret).replace(DEBUG_PLACEHOLDER, debug));

        log.info("response: {}", response);
        return response;
    }

    private String getHTMLTemplate() {
        String template = HTML_BACKUP;
        try (InputStream is = this.getClass().getResourceAsStream(TEMPLATE)) {
            if (is != null) {
                template = IOUtils.toString(is, StandardCharsets.UTF_8);
            } else {
                log.warn("file not found {}", TEMPLATE);
            }
        } catch (Exception e) {
            log.error("cannot read template", e);
        }
        return template;
    }
}