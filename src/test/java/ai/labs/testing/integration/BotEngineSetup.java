package ai.labs.testing.integration;

import ai.labs.testing.model.BotConfiguration;
import ai.labs.testing.model.PackageConfiguration;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.restassured.response.Response;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ginccc
 */
public class BotEngineSetup extends BaseCRUDOperations {
    private static final String HEADER_LOCATION = "location";
    private String REGULAR_DICTIONARY;
    private String BEHAVIOR;
    private String OUTPUT;
    private ObjectMapper objectMapper;

    public BotEngineSetup() {
        setupJsonSerializer();
    }

    private void setupJsonSerializer() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }


    public URI setupBot() throws IOException {
        super.setup();

        // load test resources
        REGULAR_DICTIONARY = load("botengine/regularDictionary.json");
        BEHAVIOR = load("botengine/behavior.json");
        OUTPUT = load("botengine/output.json");

        //create dictionary
        String locationDictionary = createResource(REGULAR_DICTIONARY, "/regulardictionarystore/regulardictionaries");

        //create behavior
        String locationBehavior = createResource(BEHAVIOR, "/behaviorstore/behaviorsets");

        //create output
        String locationOutput = createResource(OUTPUT, "/outputstore/outputsets");

        //createPackage
        PackageConfiguration packageConfig = new PackageConfiguration();
        packageConfig.getPackageExtensions().add(createNormalizerExtension());
        packageConfig.getPackageExtensions().add(createParserExtension(locationDictionary));
        packageConfig.getPackageExtensions().add(createBehaviorExtension(locationBehavior));
        packageConfig.getPackageExtensions().add(createOutputExtension(locationOutput));
        String locationPackage = createResource(toJson(packageConfig), "/packagestore/packages");


        //createBot
        BotConfiguration botConfig = new BotConfiguration();
        botConfig.getPackages().add(URI.create(locationPackage));
        return URI.create(createResource(toJson(botConfig), "/botstore/bots"));
    }

    private PackageConfiguration.PackageExtension createExtension(String type) {
        PackageConfiguration.PackageExtension packageExtension = new PackageConfiguration.PackageExtension();
        packageExtension.setType(URI.create(type));

        return packageExtension;
    }

    private PackageConfiguration.PackageExtension createNormalizerExtension() {
        PackageConfiguration.PackageExtension packageExtension = createExtension("eddi://ai.labs.normalizer");
        packageExtension.getConfig().put("allowedChars", "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!?:;.,");
        packageExtension.getConfig().put("convertUmlaute", "true");
        return packageExtension;
    }

    private PackageConfiguration.PackageExtension createParserExtension(String locationDictionary) {
        PackageConfiguration.PackageExtension packageExtension = createExtension("eddi://ai.labs.parser");
        List<PackageConfiguration.PackageExtension> dictionaries = new ArrayList<>();

        dictionaries.add(createExtension("eddi://ai.labs.parser.dictionaries.integer"));
        dictionaries.add(createExtension("eddi://ai.labs.parser.dictionaries.decimal"));
        dictionaries.add(createExtension("eddi://ai.labs.parser.dictionaries.punctuation"));
        dictionaries.add(createExtension("eddi://ai.labs.parser.dictionaries.email"));
        dictionaries.add(createExtension("eddi://ai.labs.parser.dictionaries.time"));
        dictionaries.add(createExtension("eddi://ai.labs.parser.dictionaries.ordinalNumber"));
        PackageConfiguration.PackageExtension regularDictionary =
                createExtension("eddi://ai.labs.parser.dictionaries.regular");
        regularDictionary.getConfig().put("uri", locationDictionary);
        dictionaries.add(regularDictionary);

        packageExtension.getExtensions().put("dictionaries", dictionaries.toArray());

        List<PackageConfiguration.PackageExtension> corrections = new ArrayList<>();
        PackageConfiguration.PackageExtension stemming = createExtension("eddi://ai.labs.parser.corrections.stemming");
        stemming.getConfig().put("language", "english");
        stemming.getConfig().put("lookupIfKnown", "false");
        corrections.add(stemming);
        PackageConfiguration.PackageExtension levenshtein = createExtension("eddi://ai.labs.parser.corrections.levenshtein");
        levenshtein.getConfig().put("distance", "2");
        corrections.add(levenshtein);
        corrections.add(createExtension("eddi://ai.labs.parser.corrections.mergedTerms"));

        packageExtension.getExtensions().put("corrections", corrections.toArray());
        return packageExtension;
    }

    private PackageConfiguration.PackageExtension createBehaviorExtension(String locationBehavior) {
        PackageConfiguration.PackageExtension extension = createExtension("eddi://ai.labs.behavior");
        extension.getConfig().put("uri", locationBehavior);
        return extension;
    }

    private PackageConfiguration.PackageExtension createOutputExtension(String locationOutput) {
        PackageConfiguration.PackageExtension extension = createExtension("eddi://ai.labs.output");
        extension.getConfig().put("uri", locationOutput);
        return extension;
    }

    private String createResource(String body, String resourceUri) {
        Response response = create(body, resourceUri);
        return response.getHeader(HEADER_LOCATION);
    }

    private String toJson(Object obj) throws IOException {
        StringWriter writer = new StringWriter();
        objectMapper.writeValue(writer, obj);
        return writer.toString();
    }
}
