package ai.labs.testing.integration;

import ai.labs.testing.ResourceId;
import ai.labs.testing.UriUtilities;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author ginccc
 */
@Slf4j
public class RestBotEngineTest extends BaseCRUDOperations {
    private static final String HEADER_LOCATION = "location";
    private static final String DEPLOY_PATH = "administration/unrestricted/deploy/%s?version=%s";
    private static final String DEPLOYMENT_STATUS_PATH = "administration/unrestricted/deploymentstatus/%s?version=%s";
    private ResourceId botResourceId;
    private ResourceId bot2ResourceId;
    private ResourceId conversationResourceId;

    public enum Status {
        READY,
        IN_PROGRESS,
        //NOT_FOUND,
        ERROR
    }

    @BeforeTest
    public void setup() throws IOException {
        super.setup();
        try {
            botResourceId = deployBot("botengine/regularDictionary.json",
                    "botengine/behavior.json",
                    "botengine/output.json");

            bot2ResourceId = deployBot(
                    "botengine/regularDictionary2.json",
                    "botengine/behavior2.json",
                    "botengine/output2.json");
        } catch (InterruptedException e) {
            throw new IOException(e.getLocalizedMessage(), e);
        }
    }

    private ResourceId deployBot(String regularDictionaryPath, String behaviorPath, String outputPath)
            throws IOException, InterruptedException {
        URI botLocationUri = new BotEngineSetup().setupBot(regularDictionaryPath, behaviorPath, outputPath);
        ResourceId resourceId = UriUtilities.extractResourceId(botLocationUri);
        deployBot(resourceId.getId(), resourceId.getVersion());
        log.info(String.format("bot (id=%s , version=%s) has been deployed",
                resourceId.getId(),
                resourceId.getVersion()));

        return resourceId;
    }

    @BeforeMethod
    public void beforeMethod() throws IOException {
        conversationResourceId = createConversation(botResourceId.getId());
    }

    private void deployBot(String id, Integer version) throws InterruptedException {
        given().post(String.format(DEPLOY_PATH, id, version));

        while (true) {
            Response response = given().accept(ContentType.TEXT).
                    get(String.format(DEPLOYMENT_STATUS_PATH, id, version));
            Status status = Status.valueOf(response.getBody().print());
            if (status.equals(Status.IN_PROGRESS)) {
                Thread.sleep(500);
            } else if (status.equals(Status.ERROR)) {
                throw new RuntimeException(String.format("Couldn't deploy Bot (id=%s,version=%s)", id, version));
            } else if (status.equals(Status.READY)) {
                break;
            }
        }
    }

    private ResourceId createConversation(String id) {
        Response response = given().post("bots/unrestricted/" + id);
        String locationConversation = response.getHeader(HEADER_LOCATION);
        return UriUtilities.extractResourceId(URI.create(locationConversation));
    }

    @Test
    public void checkWelcomeMessage() {
        Response response = getConversationLogResponse(botResourceId, conversationResourceId, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(1)).
                body("conversationSteps[0].data[0].key", equalTo("actions")).
                body("conversationSteps[0].data[0].value[0]", equalTo("welcome")).
                body("conversationSteps[0].data[1].key", equalTo("output:final")).
                body("conversationSteps[0].data[1].value", equalTo("Welcome! I am E.D.D.I.")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkNormalizer() {
        sendUserInput(botResourceId, conversationResourceId, "hello123");
        Response response = getConversationLogResponse(botResourceId, conversationResourceId, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].data[0].key", equalTo("input:initial")).
                body("conversationSteps[1].data[0].value", equalTo("hello123")).
                body("conversationSteps[1].data[1].key", equalTo("input:formatted")).
                body("conversationSteps[1].data[1].value", equalTo("hello")).
                body("conversationSteps[1].data[2].key", equalTo("expressions:parsed")).
                body("conversationSteps[1].data[2].value", equalTo("greeting(hello)")).
                body("conversationSteps[1].data[3].key", equalTo("behavior_rules:success")).
                body("conversationSteps[1].data[3].value[0]", equalTo("Greeting")).
                body("conversationSteps[1].data[4].key", equalTo("actions")).
                body("conversationSteps[1].data[4].value[0]", equalTo("greet")).
                body("conversationSteps[1].data[5].key", equalTo("output:action:greet")).
                body("conversationSteps[1].data[5].value", equalTo("Hi there! Nice to meet up! :-)")).
                body("conversationSteps[1].data[6].key", equalTo("output:final")).
                body("conversationSteps[1].data[6].value", equalTo("Hi there! Nice to meet up! :-)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkHelloInputSimpleConversationLog() {
        sendUserInput(botResourceId, conversationResourceId, "hello");
        Response response = getConversationLogResponse(botResourceId, conversationResourceId, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].data[0].key", equalTo("input:initial")).
                body("conversationSteps[1].data[0].value", equalTo("hello")).
                body("conversationSteps[1].data[1].key", equalTo("actions")).
                body("conversationSteps[1].data[1].value[0]", equalTo("greet")).
                body("conversationSteps[1].data[2].key", equalTo("output:final")).
                body("conversationSteps[1].data[2].value", equalTo("Hi there! Nice to meet up! :-)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkSecondTimeHelloInputSimpleConversationLog() {
        sendUserInput(botResourceId, conversationResourceId, "hello");
        sendUserInput(botResourceId, conversationResourceId, "hello");
        Response response = getConversationLogResponse(botResourceId, conversationResourceId, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(3)).
                body("conversationSteps[2].data[0].key", equalTo("input:initial")).
                body("conversationSteps[2].data[0].value", equalTo("hello")).
                body("conversationSteps[2].data[1].key", equalTo("actions")).
                body("conversationSteps[2].data[1].value[0]", equalTo("greet")).
                body("conversationSteps[2].data[2].key", equalTo("output:final")).
                body("conversationSteps[2].data[2].value", equalTo("Did we already say hi ?! Well, twice is better " +
                        "than not at all! ;-)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkHelloInputComplexConversationLog() {
        sendUserInput(botResourceId, conversationResourceId, "hello");
        Response response = getConversationLogResponse(botResourceId, conversationResourceId, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].data[0].key", equalTo("input:initial")).
                body("conversationSteps[1].data[0].value", equalTo("hello")).
                body("conversationSteps[1].data[1].key", equalTo("input:formatted")).
                body("conversationSteps[1].data[1].value", equalTo("hello")).
                body("conversationSteps[1].data[2].key", equalTo("expressions:parsed")).
                body("conversationSteps[1].data[2].value", equalTo("greeting(hello)")).
                body("conversationSteps[1].data[3].key", equalTo("behavior_rules:success")).
                body("conversationSteps[1].data[3].value[0]", equalTo("Greeting")).
                body("conversationSteps[1].data[4].key", equalTo("actions")).
                body("conversationSteps[1].data[4].value[0]", equalTo("greet")).
                body("conversationSteps[1].data[5].key", equalTo("output:action:greet")).
                body("conversationSteps[1].data[5].value", equalTo("Hi there! Nice to meet up! :-)")).
                body("conversationSteps[1].data[6].key", equalTo("output:final")).
                body("conversationSteps[1].data[6].value", equalTo("Hi there! Nice to meet up! :-)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkQuickReplyConversationLog() {
        sendUserInput(botResourceId, conversationResourceId, "bye");
        Response response = getConversationLogResponse(botResourceId, conversationResourceId, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].data[0].key", equalTo("input:initial")).
                body("conversationSteps[1].data[0].value", equalTo("bye")).
                body("conversationSteps[1].data[1].key", equalTo("actions")).
                body("conversationSteps[1].data[1].value[0]", equalTo("say_goodbye")).
                body("conversationSteps[1].data[1].value[1]", equalTo("CONVERSATION_END")).
                body("conversationSteps[1].data[2].key", equalTo("output:quickreply:say_goodbye")).
                body("conversationSteps[1].data[2].value[0].value", equalTo("Bye, bye!")).
                body("conversationSteps[1].data[2].value[0].expressions", equalTo("goodbye(bye_bye), operation(quick_reply)")).
                body("conversationSteps[1].data[2].value[1].value", equalTo("See you!")).
                body("conversationSteps[1].data[2].value[1].expressions", equalTo("goodbye(see_you), operation" +
                        "(quick_reply)")).
                body("conversationSteps[1].data[3].key", equalTo("output:final")).
                body("conversationSteps[1].data[3].value", equalTo("See you soon!")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo("ENDED")).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkHelloInputComplexConversationLogWithSecondBotDeployed() {
        ResourceId conversationResourceId2 = createConversation(bot2ResourceId.getId());
        sendUserInput(bot2ResourceId, conversationResourceId2, "hi");
        Response response = getConversationLogResponse(bot2ResourceId, conversationResourceId2, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(bot2ResourceId.getId())).
                body("botVersion", equalTo(bot2ResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].data[0].key", equalTo("input:initial")).
                body("conversationSteps[1].data[0].value", equalTo("hi")).
                body("conversationSteps[1].data[1].key", equalTo("input:formatted")).
                body("conversationSteps[1].data[1].value", equalTo("hi")).
                body("conversationSteps[1].data[2].key", equalTo("expressions:parsed")).
                body("conversationSteps[1].data[2].value", equalTo("greeting(hi)")).
                body("conversationSteps[1].data[3].key", equalTo("behavior_rules:success")).
                body("conversationSteps[1].data[3].value[0]", equalTo("Greeting")).
                body("conversationSteps[1].data[4].key", equalTo("actions")).
                body("conversationSteps[1].data[4].value[0]", equalTo("greet2")).
                body("conversationSteps[1].data[5].key", equalTo("output:action:greet2")).
                body("conversationSteps[1].data[5].value", equalTo("Hi there! Nice to meet up! :-)")).
                body("conversationSteps[1].data[6].key", equalTo("output:final")).
                body("conversationSteps[1].data[6].value", equalTo("Hi there! Nice to meet up! :-)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    private void sendUserInput(ResourceId resourceId, ResourceId conversationResourceId, String userInput) {
        given().
                contentType(ContentType.TEXT).
                body(userInput).
                post(String.format("bots/unrestricted/%s/%s", resourceId.getId(), conversationResourceId.getId()));
    }

    private Response getConversationLogResponse(ResourceId botResourceId, ResourceId conversationResourceId, boolean includeAll) {
        return given().
                contentType(ContentType.JSON).
                get(String.format("bots/unrestricted/%s/%s?includeAll=%s", botResourceId.getId(),
                        conversationResourceId.getId(), includeAll));
    }
}
