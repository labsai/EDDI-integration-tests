package ai.labs.testing.integration;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author ginccc
 */
public class RestOutputTest extends BaseCRUDOperations {
    private static final String ROOT_PATH = "/outputstore/outputsets/";
    private static final String RESOURCE_URI = "eddi://ai.labs.output" + ROOT_PATH;

    private String TEST_JSON;
    private String TEST_JSON2;
    private String PATCH_JSON;

    @BeforeTest
    public void setup() throws IOException {
        super.setup();

        // load test resources
        TEST_JSON = load("output/createOutput.json");
        TEST_JSON2 = load("output/updateOutput.json");
        PATCH_JSON = load("output/patchOutput.json");
    }

    @Test()
    public void createOutput() {
        create(TEST_JSON, ROOT_PATH, RESOURCE_URI);
    }

    @Test(dependsOnMethods = "createOutput")
    public void readOutput() {
        read(ROOT_PATH).
                body("outputs[0].key", equalTo("welcome")).
                body("outputs[1].outputValues[1]", equalTo("Hey you!"));
    }

    @Test(dependsOnMethods = "readOutput")
    public void updateOutput() {
        update(TEST_JSON2, ROOT_PATH, RESOURCE_URI).
                body("outputs[5].outputValues[0]", endsWith("--changed!"));
    }

    @Test(dependsOnMethods = "updateOutput")
    public void patchOutput() {
        patch(PATCH_JSON, ROOT_PATH, RESOURCE_URI).
                body("outputs[5].outputValues[0]", endsWith("--changed-again!"));
    }

    @Test(dependsOnMethods = "patchOutput")
    public void deleteOutput() {
        delete(ROOT_PATH);
    }
}
