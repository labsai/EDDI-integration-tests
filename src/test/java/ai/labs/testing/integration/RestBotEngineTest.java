package ai.labs.testing.integration;

import ai.labs.testing.ResourceId;
import ai.labs.testing.UriUtilities;
import ai.labs.testing.model.InputData;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author ginccc
 */
@Slf4j
public class RestBotEngineTest extends BaseCRUDOperations {
    private static final String HEADER_LOCATION = "location";
    private static final String DEPLOY_PATH = "administration/unrestricted/deploy/%s?version=%s&autoDeploy=false";
    private static final String DEPLOYMENT_STATUS_PATH = "administration/unrestricted/deploymentstatus/%s?version=%s";
    private final JsonSerialization jsonSerialization;
    private ResourceId botResourceId;
    private ResourceId bot2ResourceId;
    private ResourceId conversationResourceId;

    public enum Status {
        READY,
        IN_PROGRESS,
        //NOT_FOUND,
        ERROR,
        ENDED
    }

    public RestBotEngineTest() {
        jsonSerialization = JsonSerialization.getInstance();
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
    public void checkWelcomeMessage() throws InterruptedException {
        //since asynchronous,getting the conversationLog could be to fast
        // if the machine on which eddi will be executed is too slow, thus wait a second to be sure it is done
        Thread.sleep(1000L);
        Response response = getConversationLogResponse(botResourceId, conversationResourceId, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(1)).
                body("conversationSteps[0].conversationStep[0].key", equalTo("actions")).
                body("conversationSteps[0].conversationStep[0].value[0]", equalTo("welcome")).
                body("conversationSteps[0].conversationStep[1].key", equalTo("output:text:welcome")).
                body("conversationSteps[0].conversationStep[1].value", equalTo("Welcome! I am E.D.D.I.")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkNormalizer() {
        Response response = sendUserInput(botResourceId, conversationResourceId, "hello!!!", true, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[0].key", equalTo("input:initial")).
                body("conversationSteps[1].conversationStep[0].value", equalTo("hello!!!")).
                body("conversationSteps[1].conversationStep[1].key", equalTo("input:formatted")).
                body("conversationSteps[1].conversationStep[1].value", equalTo("hello")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkWordInputSimpleConversationLog() {
        Response response = sendUserInput(botResourceId, conversationResourceId, "hello", false, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[0].key", equalTo("input:initial")).
                body("conversationSteps[1].conversationStep[0].value", equalTo("hello")).
                body("conversationSteps[1].conversationStep[1].key", equalTo("actions")).
                body("conversationSteps[1].conversationStep[1].value[0]", equalTo("greet")).
                body("conversationSteps[1].conversationStep[2].key", equalTo("output:text:greet")).
                body("conversationSteps[1].conversationStep[2].value", equalTo("Hi there! Nice to meet up! :-)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkWordInputSimpleConversationLogReturningOnlyCurrentStep() {
        Response response = sendUserInput(botResourceId, conversationResourceId, "hello", false, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(1)).
                body("conversationSteps[0].conversationStep[0].key", equalTo("input:initial")).
                body("conversationSteps[0].conversationStep[0].value", equalTo("hello")).
                body("conversationSteps[0].conversationStep[1].key", equalTo("actions")).
                body("conversationSteps[0].conversationStep[1].value[0]", equalTo("greet")).
                body("conversationSteps[0].conversationStep[2].key", equalTo("output:text:greet")).
                body("conversationSteps[0].conversationStep[2].value", equalTo("Hi there! Nice to meet up! :-)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkSecondTimeWordInputSimpleConversationLog() {
        sendUserInput(botResourceId, conversationResourceId, "hello", false, false);
        Response response = sendUserInput(botResourceId, conversationResourceId, "hello", false, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(3)).
                body("conversationSteps[2].conversationStep[2].key", equalTo("output:text:greet")).
                body("conversationSteps[2].conversationStep[2].value", equalTo("Did we already say hi ?! Well, twice is better than not at all! ;-)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkWordInputComplexConversationLog() {
        Response response = sendUserInput(botResourceId, conversationResourceId, "hello", true, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[0].key", equalTo("input:initial")).
                body("conversationSteps[1].conversationStep[0].value", equalTo("hello")).
                body("conversationSteps[1].conversationStep[1].key", equalTo("input:formatted")).
                body("conversationSteps[1].conversationStep[1].value", equalTo("hello")).
                body("conversationSteps[1].conversationStep[2].key", equalTo("expressions:parsed")).
                body("conversationSteps[1].conversationStep[2].value", equalTo("greeting(hello)")).
                body("conversationSteps[1].conversationStep[3].key", equalTo("behavior_rules:success")).
                body("conversationSteps[1].conversationStep[3].value[0]", equalTo("Greeting")).
                body("conversationSteps[1].conversationStep[5].key", equalTo("actions")).
                body("conversationSteps[1].conversationStep[5].value[0]", equalTo("greet")).
                body("conversationSteps[1].conversationStep[6].key", equalTo("output:text:greet")).
                body("conversationSteps[1].conversationStep[6].value", equalTo("Hi there! Nice to meet up! :-)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkPhraseInputComplexConversationLog() {
        Response response = sendUserInput(botResourceId, conversationResourceId, "good afternoon", true, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[0].key", equalTo("input:initial")).
                body("conversationSteps[1].conversationStep[0].value", equalTo("good afternoon")).
                body("conversationSteps[1].conversationStep[1].key", equalTo("input:formatted")).
                body("conversationSteps[1].conversationStep[1].value", equalTo("good afternoon")).
                body("conversationSteps[1].conversationStep[2].key", equalTo("expressions:parsed")).
                body("conversationSteps[1].conversationStep[2].value", equalTo("greeting(good_afternoon)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkQuickReplyConversationLog() {
        Response response = sendUserInput(botResourceId, conversationResourceId, "question", false, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[3].key", equalTo("quickReplies:giving_two_options")).
                body("conversationSteps[1].conversationStep[3].value[0].value", equalTo("Option 1")).
                body("conversationSteps[1].conversationStep[3].value[0].expressions", equalTo("quickReply(option1)")).
                body("conversationSteps[1].conversationStep[3].value[1].value", equalTo("Option 2")).
                body("conversationSteps[1].conversationStep[3].value[1].expressions", equalTo("quickReply(option2)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo("READY")).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void checkQuickReplyRecognizedByParserConversationLog() {
        sendUserInput(botResourceId, conversationResourceId, "question", false, false);
        Response response = sendUserInput(botResourceId, conversationResourceId, "Option 1", true, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(3)).
                body("conversationSteps[2].conversationStep[2].key", equalTo("expressions:parsed")).
                body("conversationSteps[2].conversationStep[2].value", equalTo("quickReply(option1)"));
    }

    @Test
    public void checkWordInputComplexConversationLogWithSecondBotDeployed() {
        ResourceId conversationResourceId2 = createConversation(bot2ResourceId.getId());
        Response response = sendUserInput(bot2ResourceId, conversationResourceId2, "hi", true, false);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(bot2ResourceId.getId())).
                body("botVersion", equalTo(bot2ResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[0].key", equalTo("input:initial")).
                body("conversationSteps[1].conversationStep[0].value", equalTo("hi")).
                body("conversationSteps[1].conversationStep[1].key", equalTo("input:formatted")).
                body("conversationSteps[1].conversationStep[1].value", equalTo("hi")).
                body("conversationSteps[1].conversationStep[2].key", equalTo("expressions:parsed")).
                body("conversationSteps[1].conversationStep[2].value", equalTo("greeting(hi)")).
                body("conversationSteps[1].conversationStep[3].key", equalTo("behavior_rules:success")).
                body("conversationSteps[1].conversationStep[3].value[0]", equalTo("Greeting")).
                body("conversationSteps[1].conversationStep[5].key", equalTo("actions")).
                body("conversationSteps[1].conversationStep[5].value[0]", equalTo("greet2")).
                body("conversationSteps[1].conversationStep[6].key", equalTo("output:text:greet2")).
                body("conversationSteps[1].conversationStep[6].value", equalTo("Hi there! Nice to meet up! :-)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void testStringContextSendWithInput() throws IOException {
        Map<String, InputData.Context> contextMap = new HashMap<>();
        InputData.Context context = new InputData.Context(
                InputData.Context.ContextType.string, "someContextValue");
        contextMap.put("someContextKeyString", context);
        InputData inputData = new InputData("hello", contextMap);
        Response response = sendUserInputWithContext(botResourceId, conversationResourceId, inputData, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[1].key", equalTo("context:someContextKeyString")).
                body("conversationSteps[1].conversationStep[1].value.type", equalTo("string")).
                body("conversationSteps[1].conversationStep[1].value.value", equalTo("someContextValue")).
                body("conversationSteps[1].conversationStep[4].key", equalTo("behavior_rules:success")).
                body("conversationSteps[1].conversationStep[4].value[0]", equalTo("Greeting")).
                body("conversationSteps[1].conversationStep[4].value[1]", equalTo("ContextReaction1")).
                body("conversationSteps[1].conversationStep[6].key", equalTo("actions")).
                body("conversationSteps[1].conversationStep[6].value[0]", equalTo("greet")).
                body("conversationSteps[1].conversationStep[6].value[1]", equalTo("acknowledged_context1")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void testExpressionContextSendWithInput() throws IOException {
        Map<String, InputData.Context> contextMap = new HashMap<>();
        InputData.Context context = new InputData.Context(
                InputData.Context.ContextType.expressions, "expression(someValue), expression2(someOtherValue)");
        contextMap.put("someContextKeyExpressions", context);
        InputData inputData = new InputData("hello", contextMap);
        Response response = sendUserInputWithContext(botResourceId, conversationResourceId, inputData, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[1].key", equalTo("context:someContextKeyExpressions")).
                body("conversationSteps[1].conversationStep[1].value.type", equalTo("expressions")).
                body("conversationSteps[1].conversationStep[1].value.value", equalTo("expression(someValue), expression2(someOtherValue)")).
                body("conversationSteps[1].conversationStep[4].key", equalTo("behavior_rules:success")).
                body("conversationSteps[1].conversationStep[4].value[0]", equalTo("Greeting")).
                body("conversationSteps[1].conversationStep[4].value[1]", equalTo("ContextReaction2")).
                body("conversationSteps[1].conversationStep[6].key", equalTo("actions")).
                body("conversationSteps[1].conversationStep[6].value[0]", equalTo("greet")).
                body("conversationSteps[1].conversationStep[6].value[1]", equalTo("acknowledged_context2")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void testObjectContextSendWithInput() throws IOException {
        Map<String, InputData.Context> contextMap = new HashMap<>();
        Object valueObject = jsonSerialization.toObject("{\"key\":\"value\"}", Object.class);
        InputData.Context context = new InputData.Context(
                InputData.Context.ContextType.object, valueObject);
        contextMap.put("someContextKeyObject", context);
        InputData inputData = new InputData("hello", contextMap);
        Response response = sendUserInputWithContext(botResourceId, conversationResourceId, inputData, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[1].key", equalTo("context:someContextKeyObject")).
                body("conversationSteps[1].conversationStep[1].value.type", equalTo("object")).
                body("conversationSteps[1].conversationStep[4].key", equalTo("behavior_rules:success")).
                body("conversationSteps[1].conversationStep[4].value[0]", equalTo("Greeting")).
                body("conversationSteps[1].conversationStep[4].value[1]", equalTo("ContextReaction3")).
                body("conversationSteps[1].conversationStep[6].key", equalTo("actions")).
                body("conversationSteps[1].conversationStep[6].value[0]", equalTo("greet")).
                body("conversationSteps[1].conversationStep[6].value[1]", equalTo("acknowledged_context3")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void testQuickReplyAsContext() throws IOException {
        Map<String, InputData.Context> contextMap = new HashMap<>();
        Object valueObject = jsonSerialization.toObject("[{\"value\":\"qr1\",\"expressions\":\"exp(qr1)\"}," +
                "{\"value\":\"qr2\",\"expressions\":\"exp(qr2)\"}]", Object.class);
        InputData.Context context = new InputData.Context(
                InputData.Context.ContextType.object, valueObject);
        contextMap.put("quickReplies", context);
        InputData inputData = new InputData("", contextMap);
        Response response = sendUserInputWithContext(botResourceId, conversationResourceId, inputData, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[2].key", equalTo("quickReplies:context")).
                body("conversationSteps[1].conversationStep[2].value[0].value", equalTo("qr1")).
                body("conversationSteps[1].conversationStep[2].value[0].expressions", equalTo("exp(qr1)")).
                body("conversationSteps[1].conversationStep[2].value[1].value", equalTo("qr2")).
                body("conversationSteps[1].conversationStep[2].value[1].expressions", equalTo("exp(qr2)"));
    }

    @Test
    public void testTemplatingOfOutput() throws IOException {
        Map<String, InputData.Context> contextMap = new HashMap<>();
        Object valueObject = jsonSerialization.toObject("{\"username\":\"John\"}", Object.class);
        InputData.Context context = new InputData.Context(
                InputData.Context.ContextType.object, valueObject);
        contextMap.put("userInfo", context);
        InputData inputData = new InputData("hello", contextMap);
        Response response = sendUserInputWithContext(botResourceId, conversationResourceId, inputData, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[7].key", equalTo("output:text:greet_personally")).
                body("conversationSteps[1].conversationStep[7].value", equalTo("Hello John! Nice to meet you! :-)")).
                body("conversationSteps[1].conversationStep[8].key", equalTo("output:text:greet_personally:preTemplated")).
                body("conversationSteps[1].conversationStep[8].value", equalTo("Hello [[${userInfo.username}]]! Nice to meet you! :-)")).
                body("conversationSteps[1].conversationStep[9].key", equalTo("output:text:greet_personally:postTemplated")).
                body("conversationSteps[1].conversationStep[9].value", equalTo("Hello John! Nice to meet you! :-)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void testTemplatingOfQuickReply() throws IOException {
        Map<String, InputData.Context> contextMap = new HashMap<>();
        Object valueObject = jsonSerialization.toObject("{\"username\":\"John\"}", Object.class);
        InputData.Context context = new InputData.Context(
                InputData.Context.ContextType.object, valueObject);
        contextMap.put("userInfo", context);
        InputData inputData = new InputData("bye", contextMap);
        Response response = sendUserInputWithContext(botResourceId, conversationResourceId, inputData, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[11].key", equalTo("quickReplies:say_goodbye:preTemplated")).
                body("conversationSteps[1].conversationStep[11].value[0].value", equalTo("Bye, bye John!!")).
                body("conversationSteps[1].conversationStep[11].value[0].expressions", equalTo("goodbye(bye_bye), operation(quick_reply)")).
                body("conversationSteps[1].conversationStep[12].key", equalTo("quickReplies:say_goodbye:postTemplated")).
                body("conversationSteps[1].conversationStep[12].value[0].value", equalTo("Bye, bye John!!")).
                body("conversationSteps[1].conversationStep[12].value[0].expressions", equalTo("goodbye(bye_bye), operation(quick_reply)")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.ENDED.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void testPropertyExtraction() throws IOException {
        InputData inputData = new InputData("property", new HashMap<>());
        Response response = sendUserInputWithContext(botResourceId, conversationResourceId, inputData, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[4].key", equalTo("properties:extracted")).
                body("conversationSteps[1].conversationStep[4].value[0].meanings[0]", equalTo("someMeaning")).
                body("conversationSteps[1].conversationStep[4].value[0].value", equalTo("someValue")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void testPropertyExtractionWithPropertyInContext() throws IOException {
        Map<String, InputData.Context> contextMap = new HashMap<>();
        InputData.Context context = new InputData.Context(
                InputData.Context.ContextType.expressions, "property(someCategory(someValue))");
        contextMap.put("properties", context);
        InputData inputData = new InputData("", contextMap);
        Response response = sendUserInputWithContext(botResourceId, conversationResourceId, inputData, true);

        response.then().assertThat().
                statusCode(200).
                body("botId", equalTo(botResourceId.getId())).
                body("botVersion", equalTo(botResourceId.getVersion())).
                body("conversationSteps", hasSize(2)).
                body("conversationSteps[1].conversationStep[0].key", equalTo("context:properties")).
                body("conversationSteps[1].conversationStep[0].value.type", equalTo("expressions")).
                body("conversationSteps[1].conversationStep[0].value.value", equalTo("property(someCategory(someValue))")).
                body("conversationSteps[1].conversationStep[2].key", equalTo("properties:extracted")).
                body("conversationSteps[1].conversationStep[2].value[0].meanings[0]", equalTo("someCategory")).
                body("conversationSteps[1].conversationStep[2].value[0].value", equalTo("someValue")).
                body("environment", equalTo("unrestricted")).
                body("conversationState", equalTo(Status.READY.toString())).
                body("redoCacheSize", equalTo(0));
    }

    @Test
    public void testConversationEnded() throws IOException {
        Map<String, InputData.Context> contextMap = new HashMap<>();
        Object valueObject = jsonSerialization.toObject("{\"username\":\"John\"}", Object.class);
        InputData.Context context = new InputData.Context(
                InputData.Context.ContextType.object, valueObject);
        contextMap.put("userInfo", context);
        InputData inputData = new InputData("bye", contextMap);
        sendUserInputWithContext(botResourceId, conversationResourceId, inputData, true);
        Thread.sleep(100L);
        Response response = sendUserInputWithContext(botResourceId, conversationResourceId, inputData, true);

        response.then().assertThat().
                statusCode(410).
                body(equalTo("Conversation has ended!"));
    }

    private Response sendUserInput(ResourceId resourceId,
                                   ResourceId conversationResourceId,
                                   String userInput,
                                   boolean returnDetailed,
                                   boolean returnCurrentStepOnly) {
        return given().
                contentType(ContentType.TEXT).
                body(userInput).
                post(String.format("bots/unrestricted/%s/%s?returnDetailed=%s&returnCurrentStepOnly=%s",
                        resourceId.getId(), conversationResourceId.getId(), returnDetailed, returnCurrentStepOnly));
    }

    private Response sendUserInputWithContext(ResourceId resourceId,
                                              ResourceId conversationResourceId,
                                              InputData inputData,
                                              boolean returnDetailed) throws IOException {
        return given().
                contentType(ContentType.JSON).
                body(jsonSerialization.toJson(inputData)).
                post(String.format("bots/unrestricted/%s/%s?returnDetailed=%s&returnCurrentStepOnly=%s",
                        resourceId.getId(),
                        conversationResourceId.getId(),
                        returnDetailed, false));
    }

    private Response getConversationLogResponse(ResourceId botResourceId, ResourceId conversationResourceId, boolean returnDetailed) {
        return given().
                contentType(ContentType.JSON).
                get(String.format("bots/unrestricted/%s/%s?returnDetailed=%s", botResourceId.getId(),
                        conversationResourceId.getId(), returnDetailed));
    }
}
