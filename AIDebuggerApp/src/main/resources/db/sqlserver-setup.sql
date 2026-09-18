-- Optional, manually reviewed setup for Azure SQL / SQL Server.
-- Back up existing data first. This script does not drop tables or legacy columns.
-- Hibernate ddl-auto=update performs equivalent schema setup for the hackathon.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.DEBUG_INCIDENTS', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.DEBUG_INCIDENTS (
        ID bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
        APPLICATION_NAME varchar(100) NULL,
        EXCEPTION_TYPE varchar(500) NULL,
        ERROR_MESSAGE varchar(max) NULL,
        STACK_TRACE varchar(max) NULL,
        REQUEST_PATH varchar(2048) NULL,
        CREATED_AT datetime2(7) NULL
    );
END;

IF COL_LENGTH(N'dbo.DEBUG_INCIDENTS', N'APPLICATION_NAME') IS NULL
    ALTER TABLE dbo.DEBUG_INCIDENTS ADD APPLICATION_NAME varchar(100) NULL;

IF COL_LENGTH(N'dbo.DEBUG_INCIDENTS', N'REQUEST_PATH') IS NULL
    ALTER TABLE dbo.DEBUG_INCIDENTS ADD REQUEST_PATH varchar(2048) NULL;

-- The request contract allows 10,000 characters, beyond SQL Server varchar(8000).
IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.DEBUG_INCIDENTS')
      AND name = N'ERROR_MESSAGE' AND max_length <> -1
)
    ALTER TABLE dbo.DEBUG_INCIDENTS ALTER COLUMN ERROR_MESSAGE varchar(max) NULL;

IF OBJECT_ID(N'dbo.INCIDENT_ANALYSES', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.INCIDENT_ANALYSES (
        ID bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
        INCIDENT_ID bigint NOT NULL,
        STATUS varchar(20) NOT NULL,
        MODEL varchar(200) NOT NULL,
        STARTED_AT datetimeoffset(7) NOT NULL,
        COMPLETED_AT datetimeoffset(7) NULL,
        FAILURE_REASON varchar(500) NULL,
        ROOT_CAUSE varchar(max) NULL,
        SUGGESTED_FIX varchar(max) NULL,
        AFFECTED_FILE varchar(1000) NULL,
        AFFECTED_CLASS varchar(500) NULL,
        AFFECTED_METHOD varchar(500) NULL,
        AFFECTED_LINE int NULL,
        EXAMPLE_CODE varchar(max) NULL,
        CONSTRAINT FK_INCIDENT_ANALYSES_INCIDENT
            FOREIGN KEY (INCIDENT_ID) REFERENCES dbo.DEBUG_INCIDENTS(ID),
        CONSTRAINT CK_INCIDENT_ANALYSES_STATUS
            CHECK (STATUS IN ('ANALYZING', 'SUCCEEDED', 'FAILED'))
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.INCIDENT_ANALYSES')
      AND name = N'IX_INCIDENT_ANALYSES_INCIDENT'
)
    CREATE INDEX IX_INCIDENT_ANALYSES_INCIDENT
        ON dbo.INCIDENT_ANALYSES(INCIDENT_ID, ID);

COMMIT TRANSACTION;
