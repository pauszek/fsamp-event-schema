# FSAMP Event Schema

[![JSON Schema](https://img.shields.io/badge/JSON%20Schema-Draft--07-blue)](https://json-schema.org/)
[![FIPS 140-3](https://img.shields.io/badge/FIPS-140--3--oriented-green)](https://csrc.nist.gov/publications/detail/fips/140/3/final)

Canonical event contract for the FSAMP file-processing flow. The JSON contract and packaged schema use version **1.2.0**. Repository releases may use a newer patch version (for example **1.2.1**) when the packaged contract is unchanged; such a release remains wire-compatible with contract `1.2.0`.

## Event types

Every event contains the common envelope and file context:

- `schemaVersion`, `fileId`, `eventId`, `correlationId`, `timestamp`
- `source`, `eventType`
- `fileMetadata`, `storageLocation`, `securityContext`

Producer and payload semantics are enforced by JSON Schema:

| Event type | Producer | Additional payload |
|---|---|---|
| `FILE_UPLOADED` | `fsamp-gateway` | none |
| `FILE_SCANNED` | `fsamp-processor` | none |
| `ANALYSIS_COMPLETED` | `fsamp-processor` | required `processingResult` |
| `PROCESSING_FAILED` | `fsamp-processor` | required `failure` |

`fileId` and `correlationId` are UUID v4. `eventId` may be UUID v4 or deterministic UUID v5. Timestamps are UTC. KMS key ARNs accept standard and multi-Region (`mrk-...`) keys; aliases are intentionally rejected from published events.

### Completed analysis

```json
{
  "schemaVersion": "1.2.0",
  "fileId": "550e8400-e29b-41d4-a716-446655440000",
  "eventId": "f47ac10b-58cc-5372-a567-0e02b2c3d479",
  "correlationId": "9b2c9fa4-8d5d-4f75-8de8-86335fcd4621",
  "timestamp": "2026-07-11T10:02:00Z",
  "source": "fsamp-processor",
  "eventType": "ANALYSIS_COMPLETED",
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
  },
  "processingResult": {
    "isSafe": true,
    "findings": [],
    "processedAt": "2026-07-11T10:02:00Z"
  }
}
```

## Breaking changes in 1.2.0

- Event type and producer combinations are now enforced.
- Processor result and failure payloads have explicit shapes.
- Processor output events carry the same file, storage and security context as input events.
- `eventId` supports UUID v4 and v5; aggregate and correlation identifiers remain UUID v4.
- Empty files, non-UTC timestamps and invalid S3 bucket names are rejected.
- KMS multi-Region key ARNs are supported.

## Validation

```bash
mvn -B -ntp clean verify
pre-commit run --all-files
```

Gateway and processor must also validate their actual serialized producer models against this file before publishing.

## Maven distribution

The primary artifact is a JAR containing `/event.schema.json` and `/LICENSE`:

```xml
<dependency>
    <groupId>io.github.pauszek</groupId>
    <artifactId>fsamp-event-schema</artifactId>
    <version>1.2.0</version>
</dependency>
```

For non-JVM consumers, the same release also attaches a ZIP:

```xml
<dependency>
    <groupId>io.github.pauszek</groupId>
    <artifactId>fsamp-event-schema</artifactId>
    <version>1.2.0</version>
    <type>zip</type>
    <classifier>schema</classifier>
</dependency>
```

## Related repositories

- [fsamp-gateway](https://github.com/pauszek/fsamp-gateway)
- [fsamp-processor](https://github.com/pauszek/fsamp-processor)
- [fsamp-infra](https://github.com/pauszek/fsamp-infra)

## License

MIT. See [LICENSE](LICENSE).
