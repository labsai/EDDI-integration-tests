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
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    static final String VERSION_STRING = "?version=";
    ResourceId resourceId;

    private static File getFile(String filePath) throws FileNotFoundException {
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

    public void setup() throws IOException {
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

    Response update(String body, String path) {
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

    Response patch(String body, String path) {
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

    ValidatableResponse delete(String requestUri) {
        return given().delete(requestUri).then().statusCode(200);
    }

    void assertDelete(String path) {
        //test
        String requestUri = path + resourceId.getId() + VERSION_STRING + resourceId.getVersion();
        delete(requestUri);
        read(requestUri).then().statusCode(404);
    }
}
