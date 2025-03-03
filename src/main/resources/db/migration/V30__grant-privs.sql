create or replace procedure grant_all_cloudsqliamuser()
    language plpgsql
as
$$
declare
begin
    IF (SELECT exists(SELECT rolname FROM pg_roles WHERE rolname = 'cloudsqliamuser')) THEN
        grant all on all tables in schema public to cloudsqliamuser;
END IF;
end;
$$;

call grant_all_cloudsqliamuser();

drop procedure grant_all_cloudsqliamuser;