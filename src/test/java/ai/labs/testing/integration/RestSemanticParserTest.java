package ai.labs.testing.integration;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * @author ginccc
 */
public class RestSemanticParserTest extends BaseCRUDOperations {
    private static final String ROOT_PATH = "/parserstore/parsers/";
    private static final String RESOURCE_URI = "eddi://ai.labs.parser" + ROOT_PATH;
    private static final String REGULARDICTIONARY_PATH = "/regulardictionarystore/regulardictionaries/";

    private String REGULAR_DICTIONARY;
    private String PARSER_CONFIG;

    private String regularDictionaryId;
    private Integer regualarDictionaryVersion;

    @BeforeTest
    public void setup() throws IOException {
        super.setup();

        // load test resources
        REGULAR_DICTIONARY = load("parser/simpleRegularDictionary.json");
        PARSER_CONFIG = load("parser/parserConfiguration.json");
    }

    @Test()
    public void createRegularDictionary() {
        create(REGULAR_DICTIONARY, REGULARDICTIONARY_PATH,
                "eddi://ai.labs.regulardictionary" + REGULARDICTIONARY_PATH);
        regularDictionaryId = resourceId.getId();
        regualarDictionaryVersion = resourceId.getVersion();
    }

    @Test(dependsOnMethods = "createRegularDictionary")
    public void createSemanticParserConfig() {
        String parserConfig = PARSER_CONFIG;
        parserConfig = parserConfig.replaceAll("<UNIQUE_ID>", resourceId.getId());
        parserConfig = parserConfig.replace("<VERSION>", resourceId.getVersion().toString());
        create(parserConfig, ROOT_PATH, RESOURCE_URI);
    }

    @Test(dependsOnMethods = "createSemanticParserConfig")
    public void runParserOnWord() {
        //test
        Response response = given().
                body("hello").
                contentType(ContentType.JSON).
                post("/parser/" + resourceId.getId() + VERSION_STRING + resourceId.getVersion());

        //assert
        response.then().
                assertThat().
                statusCode(equalTo(200)).
                body("expressions", hasItem("greeting(hello)"));
    }

    @Test(dependsOnMethods = "runParserOnWord")
    public void runParserOnPhrase() {
        //test
        Response response = given().
                body("good afternoon").
                contentType(ContentType.JSON).
                post("/parser/" + resourceId.getId() + VERSION_STRING + resourceId.getVersion());

        //assert
        response.then().
                assertThat().
                statusCode(equalTo(200)).
                body("expressions", hasItem("greeting(good_afternoon)"));
    }

    @Test(dependsOnMethods = "runParserOnPhrase")
    public void runParserOnWordWithSpellingMistake() {
        //test
        Response response = given().
                body("helo").
                contentType(ContentType.JSON).
                post("/parser/" + resourceId.getId() + VERSION_STRING + resourceId.getVersion());

        //assert
        response.then().
                assertThat().
                statusCode(equalTo(200)).
                body("expressions", hasItem("greeting(hello)"));
    }

    @AfterTest
    public void deleteConfigFiles() {
        //clean up regular dictionary
        String requestUri = REGULARDICTIONARY_PATH + regularDictionaryId + VERSION_STRING + regualarDictionaryVersion;
        given().delete(requestUri).then().statusCode(200);
        given().get(requestUri).then().statusCode(404);

        //cleanup parser config
        delete(ROOT_PATH);
    }
}
