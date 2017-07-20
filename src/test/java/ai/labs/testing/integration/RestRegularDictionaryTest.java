package ai.labs.testing.integration;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * @author ginccc
 */
public class RestRegularDictionaryTest extends BaseCRUDOperations {
    private static final String ROOT_PATH = "/regulardictionarystore/regulardictionaries/";
    private static final String RESOURCE_URI = "eddi://ai.labs.regulardictionary" + ROOT_PATH;

    private String TEST_JSON;
    private String TEST_JSON2;
    private String PATCH_JSON;

    @BeforeTest
    public void setup() throws IOException {
        super.setup();

        // load test resources
        TEST_JSON = load("regularDictionary/createRegularDictionary.json");
        TEST_JSON2 = load("regularDictionary/updateRegularDictionary.json");
        PATCH_JSON = load("regularDictionary/patchRegularDictionary.json");
    }

    @Test()
    public void createRegularDictionary() {
        assertCreate(TEST_JSON, ROOT_PATH, RESOURCE_URI);
    }

    @Test(dependsOnMethods = "createRegularDictionary")
    public void readRegularDictionary() {
        assertRead(ROOT_PATH).
                body("language", equalTo("en")).
                body("words.word", hasItem("testword")).
                body("words.exp", hasItem("test_exp")).
                body("words.frequency", hasItem(0)).
                body("phrases.phrase", hasItem("Test Phrase")).
                body("phrases.exp", hasItem("phrase_exp"));
    }

    @Test(dependsOnMethods = "readRegularDictionary")
    public void updateRegularDictionary() {
        assertUpdate(TEST_JSON2, ROOT_PATH, RESOURCE_URI).
                body("language", equalTo("de")).
                body("words.word", hasItem("testword2")).
                body("words.exp", hasItem("test_exp2")).
                body("words.frequency", hasItem(1)).
                body("phrases.phrase", hasItem("Test Phrase2")).
                body("phrases.exp", hasItem("phrase_exp2"));
    }

    @Test(dependsOnMethods = "updateRegularDictionary")
    public void patchRegularDictionary() {
        assertPatch(PATCH_JSON, ROOT_PATH, RESOURCE_URI).
                body("language", equalTo("fr")).
                body("words.word", hasItem("testword2")).
                body("words.exp", hasItem("test_exp3")).
                body("words.frequency", hasItem(2)).
                body("phrases.phrase", hasItem("Test Phrase2")).
                body("phrases.exp", hasItem("phrase_exp3"));
    }


    @Test(dependsOnMethods = "patchRegularDictionary")
    public void deleteRegularDictionary() {
        assertDelete(ROOT_PATH);
    }
}
