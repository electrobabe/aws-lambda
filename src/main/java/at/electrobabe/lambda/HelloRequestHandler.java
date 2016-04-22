package at.electrobabe.lambda;

import at.electrobabe.lambda.pojo.HelloPOJO;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class HelloRequestHandler implements RequestHandler<HelloPOJO, String> {

    @Override
    public String handleRequest(HelloPOJO input, Context context) {
        LambdaLogger logger = context.getLogger();

        logger.log("lambda input: " + input);

        return "lambda!";
    }
}