package ai.labs.testing.integration;

import ai.labs.testing.ResourceId;
import ai.labs.testing.UriUtilities;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.net.URI;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * @author ginccc
 */
public class RestRegularDictionaryTest {
    private static final String RESOURCE_URI =
            "eddi://ai.labs.regulardictionary/regulardictionarystore/regulardictionaries/";
    private static final String ROOT_PATH = "/regulardictionarystore/regulardictionaries/";
    private static final String TEST_JSON = "{\"language\": \"en\",\"words\": " +
            "[{\"word\": \"testword\",\"exp\": \"test_exp\",\"frequency\": 0}]," +
            "\"phrases\": [{\"phrase\": \"Test Phrase\",\"exp\": \"phrase_exp\"}]}";

    private static final String TEST_JSON2 = "{\"language\": \"de\",\"words\": " +
            "[{\"word\": \"testword2\",\"exp\": \"test_exp2\",\"frequency\": 1}]," +
            "\"phrases\": [{\"phrase\": \"Test Phrase2\",\"exp\": \"phrase_exp2\"}]}";

    private static final String VERSION_STRING = "?version=";
    private static ResourceId resourceId;

    @BeforeTest
    public void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 7070;
    }

    @Test()
    public void createRegularDictionary() {
        //test
        Response response = given().
                body(TEST_JSON).
                contentType(ContentType.JSON).
                post(ROOT_PATH);

        //assert
        response.then().
                assertThat().
                statusCode(equalTo(201)).
                header("location", startsWith(RESOURCE_URI)).
                header("location", endsWith(VERSION_STRING + "1"));

        String location = response.getHeader("location");
        resourceId = UriUtilities.extractResourceId(URI.create(location));
    }

    @Test(dependsOnMethods = "createRegularDictionary")
    public void readRegularDictionary() {
        //test
        Response response = given().
                get(ROOT_PATH + resourceId.getId() + VERSION_STRING + resourceId.getVersion());

        //assert
        response.then().
                assertThat().
                statusCode(equalTo(200)).
                body("language", equalTo("en")).
                body("words.word", hasItem("testword")).
                body("words.exp", hasItem("test_exp")).
                body("words.frequency", hasItem(0)).
                body("phrases.phrase", hasItem("Test Phrase")).
                body("phrases.exp", hasItem("phrase_exp"));
    }

    @Test(dependsOnMethods = "readRegularDictionary")
    public void updateRegularDictionary() {
        //test
        Response response = given().
                body(TEST_JSON2).
                contentType(ContentType.JSON).
                put(ROOT_PATH + resourceId.getId() + VERSION_STRING + resourceId.getVersion());

        //assert
        response.then().
                assertThat().
                statusCode(equalTo(200)).
                header("location", startsWith(RESOURCE_URI)).
                header("location", endsWith(VERSION_STRING + "2"));

        String location = response.getHeader("location");
        resourceId = UriUtilities.extractResourceId(URI.create(location));

        response = given().
                get(ROOT_PATH + resourceId.getId() + VERSION_STRING + resourceId.getVersion());

        response.then().
                assertThat().
                statusCode(equalTo(200)).
                body("language", equalTo("de")).
                body("words.word", hasItem("testword2")).
                body("words.exp", hasItem("test_exp2")).
                body("words.frequency", hasItem(1)).
                body("phrases.phrase", hasItem("Test Phrase2")).
                body("phrases.exp", hasItem("phrase_exp2"));
    }

    @Test(dependsOnMethods = "updateRegularDictionary")
    public void deleteRegularDictionary() {
        //test
        String requestUri = ROOT_PATH + resourceId.getId() + VERSION_STRING + resourceId.getVersion();
        given().delete(requestUri).then().statusCode(200);
        given().get(requestUri).then().statusCode(404);
    }
}
