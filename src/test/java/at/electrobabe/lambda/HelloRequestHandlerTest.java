package at.electrobabe.lambda;


import at.electrobabe.lambda.pojo.HelloPOJO;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HelloRequestHandlerTest {

    @Test
    public void testHandleRequestNull() {
        HelloRequestHandler helloRequestHandler = new HelloRequestHandler();
        try {
            helloRequestHandler.handleRequest(null, null);
            fail();
        } catch (Exception e) {
            assertThat(e.toString()).isEqualTo("java.lang.NullPointerException");
        }
    }

    @Test
    public void testHandleRequest() {
        Context c = mock(Context.class);
        when(c.getLogger()).thenReturn(mock(LambdaLogger.class));

        HelloRequestHandler helloRequestHandler = new HelloRequestHandler();
        String ret = helloRequestHandler.handleRequest(HelloPOJO.builder().hello("hello").build(), c);

        assertThat(ret).isEqualTo("lambda!");
    }
}