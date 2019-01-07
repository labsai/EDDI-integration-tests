package ai.labs.testing.integration;

import ai.labs.testing.ResourceId;
import ai.labs.testing.UriUtilities;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.Properties;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * @author ginccc
 */
class BaseCRUDOperations {
    private static final String HEADER_LOCATION = "location";
    private static final String DEPLOY_PATH = "administration/unrestricted/deploy/%s?version=%s&autoDeploy=false";
    private static final String DEPLOYMENT_STATUS_PATH = "administration/unrestricted/deploymentstatus/%s?version=%s";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    static final String VERSION_STRING = "?version=";
    ResourceId resourceId;

    static File getFile(String filePath) throws FileNotFoundException {
        File file;
        URL resource = classLoader.getResource(filePath);
        if (resource != null) {
            file = new File(resource.getFile());
            return file;
        }

        throw new FileNotFoundException(String.format("FileNotFound: %s", filePath));
    }

    private static String toString(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()));
    }

    static String load(String filename) throws IOException {
        return toString(getFile("tests/" + filename));
    }

    public void setup() throws IOException, InterruptedException {
        final Properties props = System.getProperties();

        RestAssured.baseURI = props.containsKey("eddi.baseURI") ? props.getProperty("eddi.baseURI") : "http://localhost";
        RestAssured.port = props.containsKey("eddi.port") ? Integer.parseInt(props.getProperty("eddi.port")) : 7070;
    }

    Response create(String body, String path) {
        return given().
                body(body).
                contentType(ContentType.JSON).
                post(path);
    }

    void assertCreate(String body, String path, String resourceUri) {
        //test
        Response response = create(body, path);

        //assert
        response.then().
                assertThat().
                statusCode(equalTo(201)).
                header("location", startsWith(resourceUri)).
                header("location", endsWith(VERSION_STRING + "1"));

        String location = response.getHeader("location");
        resourceId = UriUtilities.extractResourceId(URI.create(location));
    }

    private Response read(String path) {
        return given().get(path);
    }

    ValidatableResponse assertRead(String path) {
        //test
        Response response = read(path + resourceId.getId() + VERSION_STRING + resourceId.getVersion());

        //assert
        return response.then().
                assertThat().
                statusCode(equalTo(200));
    }

    private Response update(String body, String path) {
        return given().
                body(body).
                contentType(ContentType.JSON).
                put(path + resourceId.getId() + VERSION_STRING + resourceId.getVersion());
    }

    ValidatableResponse assertUpdate(String body, String path, String resourceUri) {
        //test
        Response response = update(body, path);

        //assert
        response.then().
                assertThat().
                statusCode(equalTo(200)).
                header("location", startsWith(resourceUri)).
                header("location", endsWith(VERSION_STRING + "2"));

        String location = response.getHeader("location");
        resourceId = UriUtilities.extractResourceId(URI.create(location));

        response = read(path + resourceId.getId() + VERSION_STRING + resourceId.getVersion());

        return response.then().
                assertThat().
                statusCode(equalTo(200));
    }

    private Response patch(String body, String path) {
        return given().
                body(body).
                contentType(ContentType.JSON).
                patch(path + resourceId.getId() + VERSION_STRING + resourceId.getVersion());
    }

    ValidatableResponse assertPatch(String body, String path, String resourceUri) {
        //test
        Response response = patch(body, path);

        //assert
        response.then().
                assertThat().
                statusCode(equalTo(200)).
                header("location", startsWith(resourceUri)).
                header("location", endsWith(VERSION_STRING + "3"));

        String location = response.getHeader("location");
        resourceId = UriUtilities.extractResourceId(URI.create(location));

        response = read(path + resourceId.getId() + VERSION_STRING + resourceId.getVersion());

        return response.then().
                assertThat().
                statusCode(equalTo(200));
    }

    private ValidatableResponse delete(String requestUri) {
        return given().delete(requestUri).then().statusCode(200);
    }

    void assertDelete(String path) {
        //test
        String requestUri = path + resourceId.getId() + VERSION_STRING + resourceId.getVersion();
        delete(requestUri);
        read(requestUri).then().statusCode(404);
    }

    void deployBot(String id, Integer version) throws InterruptedException {
        given().post(String.format(DEPLOY_PATH, id, version));

        while (true) {
            Response response = given().accept(ContentType.TEXT).
                    get(String.format(DEPLOYMENT_STATUS_PATH, id, version));
            RestBotEngineTest.Status status = RestBotEngineTest.Status.valueOf(response.getBody().print());
            if (status.equals(RestBotEngineTest.Status.IN_PROGRESS)) {
                Thread.sleep(500);
            } else if (status.equals(RestBotEngineTest.Status.ERROR)) {
                throw new RuntimeException(String.format("Couldn't deploy Bot (id=%s,version=%s)", id, version));
            } else if (status.equals(RestBotEngineTest.Status.READY)) {
                break;
            }
        }
    }

    Response sendUserInput(ResourceId resourceId,
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

    ResourceId createConversation(String botId, String userId) {
        Response response = given().post("bots/unrestricted/" + botId + "?userId=" + userId);
        String locationConversation = response.getHeader(HEADER_LOCATION);
        return UriUtilities.extractResourceId(URI.create(locationConversation));
    }
}
