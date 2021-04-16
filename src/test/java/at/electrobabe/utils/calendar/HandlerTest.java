package at.electrobabe.utils.calendar;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyResponseEvent;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static at.electrobabe.utils.calendar.Handler.PARAM_DATE;
import static at.electrobabe.utils.calendar.Handler.PARAM_WEBCAL_URL;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * integration test
 * <p>
 * prerequisites: set AWS settings in ~/.aws/config
 */
public class HandlerTest {
    private static final String PROPERTY_FILE_NAME = "calendarUtilsTest.properties";
    private final Properties prop = new Properties();

    private static final String DATE = "2021-04-15";


    @Before
    public void setup() {
        try {
            //load a properties file from class path, inside static method
            prop.load(CalendarUtilsTest.class.getClassLoader().getResourceAsStream(PROPERTY_FILE_NAME));

            System.setProperty("AWS_REGION", prop.getProperty("AWS_REGION"));
        } catch (IOException ex) {
            fail();
        }
    }

    @Test
    public void handleRequest() {
        String webcalUrl = prop.getProperty("WEBCAL_URL", "https://outlook.office365.com/owa/calendar/123/calendar.ics");

        APIGatewayV2ProxyRequestEvent event = new APIGatewayV2ProxyRequestEvent();
        event.setQueryStringParameters(Map.of(PARAM_WEBCAL_URL, webcalUrl, PARAM_DATE, DATE));
        Context context = new TestContext();

        Handler handler = new Handler();
        APIGatewayV2ProxyResponseEvent result = handler.handleRequest(event, context);
        assertNotNull(result);
    }


    private class TestContext implements Context {
        public TestContext() {
        }

        public String getAwsRequestId() {
            return "495b12a8-xmpl-4eca-8168-160484189f99";
        }

        public String getLogGroupName() {
            return "/aws/lambda/[FUNCTION_NAME]";
        }

        public String getLogStreamName() {
            return "2020/02/26/[$LATEST]704f8dxmpla04097b9134246b8438f1a";
        }

        public String getFunctionName() {
            return "[FUNCTION_NAME]";
        }

        public String getFunctionVersion() {
            return "$LATEST";
        }

        public String getInvokedFunctionArn() {
            return "arn:aws:lambda:[AWS_REGION]:[LAMBDA_ID]:function:[FUNCTION_NAME]";
        }

        public CognitoIdentity getIdentity() {
            return null;
        }

        public ClientContext getClientContext() {
            return null;
        }

        public int getRemainingTimeInMillis() {
            return 300000;
        }

        public int getMemoryLimitInMB() {
            return 512;
        }

        public LambdaLogger getLogger() {
            return new TestLogger();
        }
    }

    private class TestLogger implements LambdaLogger {
        private final Logger logger = LoggerFactory.getLogger(TestLogger.class);

        public TestLogger() {
        }

        public void log(String message) {
            logger.info(message);
        }

        public void log(byte[] message) {
            logger.info(new String(message));
        }
    }
}