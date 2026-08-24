package io.github.pauszek.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSchemaContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern POM_REVISION = Pattern.compile("<revision>([^<]+)</revision>");
    private static JsonSchema schema;

    @BeforeAll
    static void loadSchema() throws IOException {
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                .getSchema(Files.newInputStream(Path.of("event.schema.json")));
    }

    @Test
    void acceptsFileUploadedFromGatewayWithUuidV5EventId() throws Exception {
        ObjectNode event = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        event.put("eventId", "f47ac10b-58cc-5372-a567-0e02b2c3d479");

        assertValid(event);
    }

    @Test
    void acceptsFileScannedFromProcessor() throws Exception {
        assertValid(baseEvent("FILE_SCANNED", "fsamp-processor"));
    }

    @Test
    void acceptsAnalysisCompletedWithRequiredResult() throws Exception {
        ObjectNode event = baseEvent("ANALYSIS_COMPLETED", "fsamp-processor");
        event.set("processingResult", MAPPER.readTree("""
                {
                  "isSafe": true,
                  "findings": [],
                  "processedAt": "2026-07-11T10:02:00Z",
                  "fileHashSHA256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "scanEngine": "fsamp-static-analyzer"
                }
                """));

        assertValid(event);
    }

    @Test
    void acceptsProcessingFailedWithRequiredFailure() throws Exception {
        ObjectNode event = baseEvent("PROCESSING_FAILED", "fsamp-processor");
        event.set("failure", MAPPER.readTree("""
                {
                  "code": "STORAGE_ERROR",
                  "message": "Temporary S3 failure",
                  "failedAt": "2026-07-11T10:02:00Z",
                  "retryable": true
                }
                """));

        assertValid(event);
    }

    @Test
    void enforcesProducerForEachEventType() throws Exception {
        assertInvalid(baseEvent("FILE_UPLOADED", "fsamp-processor"));
        assertInvalid(baseEvent("FILE_SCANNED", "fsamp-gateway"));

        ObjectNode completed = baseEvent("ANALYSIS_COMPLETED", "fsamp-gateway");
        completed.set("processingResult", minimalResult());
        assertInvalid(completed);

        ObjectNode failed = baseEvent("PROCESSING_FAILED", "fsamp-gateway");
        failed.set("failure", minimalFailure());
        assertInvalid(failed);
    }

    @Test
    void requiresTypeSpecificPayloadAndRejectsPayloadMixing() throws Exception {
        assertInvalid(baseEvent("ANALYSIS_COMPLETED", "fsamp-processor"));
        assertInvalid(baseEvent("PROCESSING_FAILED", "fsamp-processor"));

        ObjectNode uploadedWithResult = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        uploadedWithResult.set("processingResult", minimalResult());
        assertInvalid(uploadedWithResult);

        ObjectNode completedWithFailure = baseEvent("ANALYSIS_COMPLETED", "fsamp-processor");
        completedWithFailure.set("processingResult", minimalResult());
        completedWithFailure.set("failure", minimalFailure());
        assertInvalid(completedWithFailure);
    }

    @Test
    void enforcesIdentifierVersions() throws Exception {
        ObjectNode event = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        event.put("fileId", "f47ac10b-58cc-1372-a567-0e02b2c3d479");
        assertInvalid(event);

        event = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        event.put("correlationId", "f47ac10b-58cc-5372-a567-0e02b2c3d479");
        assertInvalid(event);

        event = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        event.put("eventId", "f47ac10b-58cc-1372-a567-0e02b2c3d479");
        assertInvalid(event);
    }

    @Test
    void acceptsStandardAndMultiRegionKmsKeyArnsButRejectsAliases() throws Exception {
        ObjectNode standard = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        standard.withObject("/securityContext").put(
                "kmsKeyId",
                "arn:aws:kms:us-west-2:123456789012:key/12345678-1234-1234-1234-123456789012"
        );
        assertValid(standard);

        ObjectNode multiRegion = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        multiRegion.withObject("/securityContext").put(
                "kmsKeyId",
                "arn:aws:kms:us-west-2:123456789012:key/mrk-1234567890abcdef1234567890abcdef"
        );
        assertValid(multiRegion);

        ObjectNode alias = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        alias.withObject("/securityContext").put("kmsKeyId", "alias/fsamp-key");
        assertInvalid(alias);
    }

    @Test
    void enforcesNonEmptyFilesUtcTimestampsAndS3BucketRules() throws Exception {
        ObjectNode event = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        event.withObject("/fileMetadata").put("fileSizeBytes", 0);
        assertInvalid(event);

        event = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        event.put("timestamp", "2026-07-11T12:00:00+02:00");
        assertInvalid(event);

        event = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        event.withObject("/storageLocation").put("bucketName", "invalid..bucket");
        assertInvalid(event);

        event = baseEvent("FILE_UPLOADED", "fsamp-gateway");
        event.withObject("/storageLocation").put("bucketName", "192.168.1.1");
        assertInvalid(event);
    }

    @Test
    void keepsContractVersionAlignedAndReleaseVersionCompatible() throws Exception {
        JsonNode document = MAPPER.readTree(Path.of("event.schema.json").toFile());
        SemanticVersion contractVersion = SemanticVersion.parse(
                document.at("/properties/schemaVersion/const").asText()
        );
        SemanticVersion pomVersion = SemanticVersion.parse(pomRevision());
        SemanticVersion releaseVersion = SemanticVersion.parse(
                Files.readString(Path.of("release.version")).trim()
        );

        assertEquals(contractVersion, pomVersion,
                "The POM packages the canonical contract version");
        assertTrue(releaseVersion.isPatchCompatibleWith(contractVersion),
                "The repository release must keep the contract major/minor and not decrease its patch");
    }

    @Test
    void acceptsOnlyPatchCompatibleReleaseWithoutChangingTheContract() {
        SemanticVersion contractVersion = SemanticVersion.parse("1.2.0");

        assertTrue(SemanticVersion.parse("1.2.0").isPatchCompatibleWith(contractVersion));
        assertTrue(SemanticVersion.parse("1.2.1").isPatchCompatibleWith(contractVersion));
        assertFalse(SemanticVersion.parse("1.1.9").isPatchCompatibleWith(contractVersion));
        assertFalse(SemanticVersion.parse("1.3.0").isPatchCompatibleWith(contractVersion));
        assertFalse(SemanticVersion.parse("2.0.0").isPatchCompatibleWith(contractVersion));
    }

    private static String pomRevision() throws IOException {
        Matcher matcher = POM_REVISION.matcher(Files.readString(Path.of("pom.xml")));
        assertTrue(matcher.find(), "pom.xml must declare a revision");
        return matcher.group(1).trim();
    }

    private static ObjectNode baseEvent(String eventType, String source) throws Exception {
        ObjectNode event = (ObjectNode) MAPPER.readTree("""
                {
                  "schemaVersion": "1.2.0",
                  "fileId": "550e8400-e29b-41d4-a716-446655440000",
                  "eventId": "123e4567-e89b-42d3-a456-426614174000",
                  "correlationId": "9b2c9fa4-8d5d-4f75-8de8-86335fcd4621",
                  "timestamp": "2026-07-11T10:00:00Z",
                  "source": "fsamp-gateway",
                  "eventType": "FILE_UPLOADED",
                  "fileMetadata": {
                    "originalFilename": "document.pdf",
                    "fileSizeBytes": 1024,
                    "mimeType": "application/pdf",
                    "checksumSHA256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                  },
                  "storageLocation": {
                    "bucketName": "fsamp-files",
                    "objectKey": "uploads/2026/07/document.pdf",
                    "region": "us-west-2"
                  },
                  "securityContext": {
                    "isEncrypted": true,
                    "encryptionAlgorithm": "AES/GCM/NoPadding",
                    "kmsKeyId": "arn:aws:kms:us-west-2:123456789012:key/mrk-1234567890abcdef1234567890abcdef"
                  }
                }
                """);
        event.put("eventType", eventType);
        event.put("source", source);
        return event;
    }

    private static JsonNode minimalResult() throws Exception {
        return MAPPER.readTree("""
                {"isSafe":true,"findings":[],"processedAt":"2026-07-11T10:02:00Z"}
                """);
    }

    private static JsonNode minimalFailure() throws Exception {
        return MAPPER.readTree("""
                {"code":"PROCESSING_ERROR","message":"Failed","failedAt":"2026-07-11T10:02:00Z","retryable":true}
                """);
    }

    private static void assertValid(JsonNode event) {
        Set<ValidationMessage> errors = schema.validate(event);
        assertTrue(errors.isEmpty(), () -> "Expected valid event, got: " + errors);
    }

    private static void assertInvalid(JsonNode event) {
        assertFalse(schema.validate(event).isEmpty(), "Expected schema validation to fail");
    }

    private record SemanticVersion(int major, int minor, int patch) {

        private static final Pattern FORMAT = Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");

        private static SemanticVersion parse(String value) {
            Matcher matcher = FORMAT.matcher(value);
            assertTrue(matcher.matches(), () -> "Expected semantic version, got: " + value);
            return new SemanticVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        }

        private boolean isPatchCompatibleWith(SemanticVersion contractVersion) {
            return major == contractVersion.major
                    && minor == contractVersion.minor
                    && patch >= contractVersion.patch;
        }
    }
}
