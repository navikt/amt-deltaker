DO
$$
BEGIN
    IF EXISTS(SELECT * FROM pg_roles WHERE rolname = 'datastream') THEN
       ALTER USER "amt-deltaker-2" WITH REPLICATION;
       CREATE PUBLICATION "ds_publication" FOR ALL TABLES;

       ALTER DEFAULT PRIVILEGES IN SCHEMA PUBLIC GRANT SELECT ON TABLES TO "datastream";
       GRANT SELECT ON ALL TABLES IN SCHEMA PUBLIC TO "datastream";
       ALTER USER "datastream" WITH REPLICATION;
    END IF;
END
$$ LANGUAGE 'plpgsql';

BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'datastream') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = 'ds_replication') THEN
            PERFORM PG_CREATE_LOGICAL_REPLICATION_SLOT('ds_replication', 'pgoutput');
        END IF;
    END IF;
END
$$ LANGUAGE 'plpgsql';