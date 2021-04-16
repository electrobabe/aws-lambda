package at.electrobabe.utils.calendar;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
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
public class Handler implements RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent> {

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

    /**
     * @param event   SQSEvent input
     * @param context Context
     * @return output json string
     */
    String handleRequest(SQSEvent event, Context context) {
        String response = "{" +
                "\"isBase64Encoded\": false," +
                "\"statusCode\": 200," +
                "\"headers\": { \"myHeader\": \"myHeaderValue\"}," +
                "\"body\": " + BODY_PLACEHOLDER +
                "}";

        String test = test(event, context);
        log.info("test: {}", test);
        String ret = response.replace(BODY_PLACEHOLDER, test);
        log.info("ret: {}", ret);

        log.info("response: {}", response);
        return response;
    }

    @Override
    public APIGatewayV2ProxyResponseEvent handleRequest(APIGatewayV2ProxyRequestEvent event, Context context) {
        log.info("event {}", gson.toJson(event));
        log.info("CONTEXT: {}", gson.toJson(context));
        String debug = "event: " + gson.toJson(event) + ", \ncontext: " + gson.toJson(context);

        if (event.getQueryStringParameters() != null &&
                event.getQueryStringParameters().containsKey(PARAM_WEBCAL_URL) && event.getQueryStringParameters().containsKey(PARAM_DATE)) {

            String text = getEvents(event, debug);

            if (!event.getQueryStringParameters().containsKey(PARAM_DEBUG)) {
                log.info("debug: {}", debug);
                debug = "";
            }
            return response(text, HttpURLConnection.HTTP_ACCEPTED, debug);

        } else {
            String noParamsMsg = "Please enter ICS / Webcal URL and pick a date";
            return response(noParamsMsg, HttpURLConnection.HTTP_NOT_ACCEPTABLE, debug);
        }
    }

    private String getEvents(APIGatewayV2ProxyRequestEvent req, String debug) {
        String webcalUrl = req.getQueryStringParameters().get(PARAM_WEBCAL_URL);
        log.info("webcalUrl: {}", webcalUrl);

        String date = req.getQueryStringParameters().get(PARAM_DATE);
        log.info("date: {}", date);

        String events = "no events found!";
        try {
            String eventsAsList = CalendarUtils.getCalendarEntriesForDay(date, webcalUrl);

            events = Arrays.stream(eventsAsList.split("\n")).map(s -> "<li>" + s + "</li>").collect(Collectors.joining("", "<ol>", "</ol>"));
            log.info("---formattedEvents {}", events);

        } catch (Exception e) {
            log.error("error getting events", e);
            debug += "<br/><b>ERROR: " + Arrays.toString(e.getStackTrace()) + "</b>";
        }

        log.debug("debug {}", debug);
        log.info("events {}", events);


        return String.format("<ul><li>webcalUrl: %s</li><li>date: %s</li><li>events: %s</li></ul>", webcalUrl, date, events);
    }

    private APIGatewayV2ProxyResponseEvent response(String ret, int httpStatusCode, String debug) {
        APIGatewayV2ProxyResponseEvent response = new APIGatewayV2ProxyResponseEvent();
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

    private String test(SQSEvent event, Context context) {
        String response = "";
        try {
            log.info("EVENT: {}", gson.toJson(event));

            // log execution details
            log.info("ENVIRONMENT VARIABLES: {}", gson.toJson(System.getenv()));
            log.info("CONTEXT: {}", gson.toJson(context));

            // process event
            if (event != null && event.getRecords() != null) {
                for (SQSMessage msg : event.getRecords()) {
                    if (msg != null) {
                        log.info(msg.getBody());
                    } else {
                        log.warn("msg is null");
                    }
                }
            } else {
                log.warn("event is null {} ", event);
            }

            // call Lambda API

            // process Lambda API response
            CompletableFuture<GetAccountSettingsResponse> accountSettings = lambdaClient.getAccountSettings(GetAccountSettingsRequest.builder().build());
            log.info("Getting account settings {}", accountSettings);

            if (accountSettings != null) {
                GetAccountSettingsResponse settings = accountSettings.get();
                response = gson.toJson(settings.accountUsage());
                log.info("Account usage: {}", response);
            }

        } catch (Exception e) {
            log.error("error handling request", e);
        }
        return response;
    }
}