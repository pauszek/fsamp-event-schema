# FSAMP Event Schema

[![JSON Schema](https://img.shields.io/badge/JSON%20Schema-Draft--07-blue)](https://json-schema.org/)
[![FIPS 140-3](https://img.shields.io/badge/FIPS-140--3-green)](https://csrc.nist.gov/publications/detail/fips/140/3/final)

> Canonical event schema for the FSAMP platform - the single source of truth for event contracts.

## Overview

This repository contains the canonical JSON Schema for FSAMP file events. Gateway and Processor validate against this contract before publishing or consuming events.

## Schema Version 1.1.2

Current schema version: **1.1.2**

### FIPS 140-3-Oriented Contract Constraints

- **Encryption**: Only AES-256-GCM (NIST SP 800-38D AEAD)
- **Hashing**: SHA-256 checksums (FIPS 180-4)
- **Key Management**: AWS KMS with ARN validation

### Breaking Changes from pre-1.1.0

| Field | Before | v1.1.2 |
|-------|--------|--------|
| `schemaVersion` | N/A | Required: `"1.1.2"` |
| `fileId` | N/A | Required aggregate UUID for the file |
| `correlationId` | String | UUID format required |
| `source` | N/A | Required: `fsamp-gateway` or `fsamp-processor` |
| `encryptionAlgorithm` | `AES-GCM` or `AES-CBC` | Only `AES/GCM/NoPadding` |
| `isEncrypted` | Boolean | Always `true` |
| `kmsKeyId` | Optional | Required with ARN pattern |
| `checksumSHA256` | N/A | Required (64 hex chars) |

## Schema Structure

```json
{
  "schemaVersion": "1.1.2",
  "fileId": "uuid",
  "eventId": "uuid",
  "correlationId": "uuid",
  "timestamp": "ISO-8601",
  "source": "fsamp-gateway | fsamp-processor",
  "eventType": "FILE_UPLOADED | FILE_SCANNED | ANALYSIS_COMPLETED",
  "fileMetadata": {
    "originalFilename": "string",
    "mimeType": "string",
    "fileSizeBytes": "number (max 100MB)",
    "checksumSHA256": "64 hex chars"
  },
  "storageLocation": {
    "bucketName": "string (S3 naming)",
    "objectKey": "string"
  },
  "securityContext": {
    "isEncrypted": true,
    "encryptionAlgorithm": "AES/GCM/NoPadding",
    "kmsKeyId": "arn:aws:kms:... or arn:aws-us-gov:kms:..."
  }
}
```

## Contract Testing

Both services implement contract tests validating against this schema:

| Service | Framework |
|---------|-----------|
| fsamp-gateway (Java) | JUnit 5 + networknt/json-schema-validator |
| fsamp-processor (Python) | pytest + jsonschema |

### Running Tests

```bash
# Java (Gateway)
cd ../fsamp-gateway
./mvnw test -Dtest=EventSchemaContractTest

# Python (Processor)
cd ../fsamp-processor
pytest tests/contract/ -v
```

## Distribution

The schema is distributed as a Maven artifact:

```xml
<dependency>
    <groupId>io.github.pauszek</groupId>
    <artifactId>fsamp-event-schema</artifactId>
    <version>1.1.2</version>
</dependency>
```

## Related Repositories

- [fsamp-gateway](https://github.com/pauszek/fsamp-gateway) - Java Spring Boot file upload service
- [fsamp-processor](https://github.com/pauszek/fsamp-processor) - Python Lambda event processor
- [fsamp-infra](https://github.com/pauszek/fsamp-infra) - Terraform infrastructure

## License

MIT
