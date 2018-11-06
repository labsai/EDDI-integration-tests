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
    public void setup() throws IOException, InterruptedException {
        super.setup();

        // load test resources
        TEST_JSON = load("output/createOutput.json");
        TEST_JSON2 = load("output/updateOutput.json");
        PATCH_JSON = load("output/patchOutput.json");
    }

    @Test()
    public void createOutput() {
        assertCreate(TEST_JSON, ROOT_PATH, RESOURCE_URI);
    }

    @Test(dependsOnMethods = "createOutput")
    public void readOutput() {
        assertRead(ROOT_PATH).
                body("outputSet[1].action", equalTo("greet")).
                body("outputSet[1].outputs[0].type", equalTo("text")).
                body("outputSet[1].outputs[0].valueAlternatives[1]", equalTo("Hey you!"));
    }

    @Test(dependsOnMethods = "readOutput")
    public void updateOutput() {
        assertUpdate(TEST_JSON2, ROOT_PATH, RESOURCE_URI).
                body("outputSet[5].outputs[0].valueAlternatives[0]", endsWith("--changed!"));
    }

    @Test(dependsOnMethods = "updateOutput")
    public void patchOutput() {
        assertPatch(PATCH_JSON, ROOT_PATH, RESOURCE_URI).
                body("outputSet[5].outputs[0].valueAlternatives[0]", endsWith("--changed-again!"));
    }

    @Test(dependsOnMethods = "patchOutput")
    public void deleteOutput() {
        assertDelete(ROOT_PATH);
    }
}
