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

DO
$$
BEGIN
    IF EXISTS(SELECT * FROM pg_roles WHERE rolname = 'datastream') THEN
       PERFORM PG_CREATE_LOGICAL_REPLICATION_SLOT('ds_replication', 'pgoutput');
    END IF;
END
$$ LANGUAGE 'plpgsql';