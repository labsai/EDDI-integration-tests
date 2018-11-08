package ai.labs.testing.integration;

import ai.labs.testing.ResourceId;
import ai.labs.testing.UriUtilities;
import io.restassured.response.Response;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class RestUseCaseTest extends BaseCRUDOperations {
    private static final String KEY_WEATHER_BOT = "weather-bot";
    private Map<String, ResourceId> bots = new HashMap<>();

    @BeforeTest
    public void setup() throws IOException, InterruptedException {
        super.setup();

        bots.put(KEY_WEATHER_BOT, importBot("weather_bot_v1"));
    }

    private ResourceId importBot(String filename) throws FileNotFoundException, InterruptedException {
        Response response = given().
                contentType("application/zip").
                basePath("/backup/import").
                body(getFile("tests/useCases/" + filename + ".zip")).
                post();

        String location = response.getHeader("location");
        ResourceId resourceId = UriUtilities.extractResourceId(URI.create(location));
        deployBot(resourceId.getId(), resourceId.getVersion());
        return resourceId;
    }

    @Test
    public void weatherBot() {
        ResourceId resourceId = bots.get(KEY_WEATHER_BOT);
        ResourceId conversationId = createConversation(resourceId.getId());
        sendUserInput(resourceId, conversationId, "weather",
                false, true);

        Response response = sendUserInput(resourceId, conversationId, "Vienna",
                false, false);

        response.then().assertThat().
                body("botId", equalTo(resourceId.getId())).
                body("botVersion", equalTo(resourceId.getVersion())).
                body("conversationSteps[1].conversationStep[1].key", equalTo("actions")).
                body("conversationSteps[1].conversationStep[1].value[0]", equalTo("ask_for_city")).
                body("conversationSteps[2].conversationStep[1].key", equalTo("actions")).
                body("conversationSteps[2].conversationStep[1].value[0]", equalTo("current_weather_in_city")).
                body("conversationSteps[2].conversationStep[2].key", equalTo("output:text:current_weather_in_city")).
                body("conversationSteps[2].conversationStep[2].value", containsString("Vienna")).
                body("conversationSteps[2].conversationStep[2].value", not(containsString("[[")));
    }

    @Test
    public void useBotManagement() throws IOException {
        final String intent = "weather-bot";
        final String userId = "12345";

        ResourceId resourceId = bots.get(KEY_WEATHER_BOT);
        given().contentType("application/json").
                body(String.format(load("useCases/botdeployment.json"), resourceId.getId())).
                put("/bottriggerstore/bottriggers/" + intent);

        given().post("/managedbots/" + intent + "/" + userId + "/endConversation");

        Response response = given().contentType("application/json").
                body("{\"input\":\"weather\"}").
                queryParam("returnCurrentStepOnly", "false").
                post("/managedbots/" + intent + "/" + userId);

        response.then().assertThat().
                body("botId", equalTo(resourceId.getId())).
                body("botVersion", equalTo(resourceId.getVersion())).
                body("conversationSteps[1].conversationStep[1].key", equalTo("actions")).
                body("conversationSteps[1].conversationStep[1].value[0]", equalTo("ask_for_city"));
    }
}