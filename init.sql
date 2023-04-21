CREATE TABLE lookup (
    id VARCHAR(512) NOT NULL PRIMARY KEY,
    archival_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    timestamp DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6) DEFAULT NULL
);

CREATE TABLE lookup_identifier (
   id VARCHAR(512) NOT NULL,
   name VARCHAR(255) NOT NULL,
   value VARCHAR(255) NOT NULL,
   INDEX lookup_identifier_lookup_idx(name, value)
);