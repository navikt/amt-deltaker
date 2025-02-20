DO
$$
    BEGIN
        IF EXISTS
                (SELECT 1 from pg_roles where rolname = 'amt-deltaker')
        THEN
            ALTER USER "amt-deltaker" WITH REPLICATION;
        END IF;
    END
$$;
END;

DO
$$
    BEGIN
        IF EXISTS
                (SELECT 1 from pg_roles where rolname = 'datastream')
        THEN
            ALTER USER "datastream" WITH REPLICATION;
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "datastream";
            GRANT USAGE ON SCHEMA public TO "datastream";
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "datastream";
        END IF;
    END
$$;
END;

DO
$$
    BEGIN
        if not exists
                (select 1 from pg_publication where pubname = 'amt_deltaker_publication')
        then
            CREATE PUBLICATION amt_deltaker_publication for ALL TABLES;
        end if;
    end;
$$;
END;

DO
$$
    BEGIN
        if not exists
            (select 1 from pg_replication_slots where slot_name = 'amt_deltaker_replication')
        then
            PERFORM PG_CREATE_LOGICAL_REPLICATION_SLOT('amt_deltaker_replication', 'pgoutput');
        end if;
    end;
$$;
END;
