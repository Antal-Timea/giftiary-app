-- Server-controlled row metadata and the reference rules that composite foreign keys cannot express.

-- ---------------------------------------------------------------------------------------------
-- Version and timestamps
-- ---------------------------------------------------------------------------------------------

-- For every synced table. Whatever the client sends, the server decides:
--   INSERT: version = 1, created_at = updated_at = now()
--   UPDATE that changes something: version = old version + 1, updated_at = now()
--   UPDATE that changes nothing: the row is left exactly as it was
-- A soft delete (deleted_at) or archive (archived_at) is an UPDATE like any other, so it bumps
-- the version and other devices see a newer row. Android keeps version 0 for rows the server has
-- never seen and adopts the server's version after each successful push.
create function private.set_sync_metadata()
    returns trigger
    language plpgsql
    set search_path = ''
as $$
begin
    if tg_op = 'INSERT' then
        new.version := 1;
        new.created_at := now();
        new.updated_at := now();
        return new;
    end if;

    if new.id is distinct from old.id then
        raise exception 'id of % % cannot be changed', tg_table_name, old.id
            using errcode = 'insufficient_privilege';
    end if;
    if new.owner_user_id is distinct from old.owner_user_id then
        raise exception 'owner_user_id of % % cannot be changed', tg_table_name, old.id
            using errcode = 'insufficient_privilege';
    end if;

    -- Ignore client-supplied metadata before deciding whether anything really changed.
    new.version := old.version;
    new.created_at := old.created_at;
    new.updated_at := old.updated_at;
    if new is not distinct from old then
        return new;
    end if;

    new.version := old.version + 1;
    new.updated_at := now();
    return new;
end;
$$;

create trigger set_sync_metadata before insert or update on public.people
    for each row execute function private.set_sync_metadata();
create trigger set_sync_metadata before insert or update on public.occasions
    for each row execute function private.set_sync_metadata();
create trigger set_sync_metadata before insert or update on public.gift_ideas
    for each row execute function private.set_sync_metadata();
create trigger set_sync_metadata before insert or update on public.gifts
    for each row execute function private.set_sync_metadata();

-- Profiles are not synced, so they carry timestamps but no version.
create function private.set_profile_timestamps()
    returns trigger
    language plpgsql
    set search_path = ''
as $$
begin
    if tg_op = 'INSERT' then
        new.created_at := now();
    else
        if new.id is distinct from old.id then
            raise exception 'profile id cannot be changed' using errcode = 'insufficient_privilege';
        end if;
        new.created_at := old.created_at;
    end if;
    new.updated_at := now();
    return new;
end;
$$;

create trigger set_profile_timestamps before insert or update on public.profiles
    for each row execute function private.set_profile_timestamps();

-- ---------------------------------------------------------------------------------------------
-- An occasion must fit the people of the idea or gift
-- ---------------------------------------------------------------------------------------------

-- The composite foreign keys already guarantee the same owner. These checks add what Android
-- also enforces: a person's occasion (e.g. Anna's birthday) can only be the occasion of an idea
-- for that person, or of a gift that person gave or received. General occasions (no person) fit
-- anything. Deleted occasions are checked the same way: soft deletion keeps references valid.
--
-- The lookups run with the caller's rights. An occasion the caller cannot see belongs to someone
-- else, which the composite foreign key rejects anyway.

create function private.check_gift_idea_occasion()
    returns trigger
    language plpgsql
    set search_path = ''
as $$
declare
    occasion_person_id uuid;
begin
    if new.occasion_id is null then
        return new;
    end if;
    select o.person_id into occasion_person_id from public.occasions o where o.id = new.occasion_id;
    if occasion_person_id is not null and occasion_person_id <> new.recipient_person_id then
        raise exception 'occasion % belongs to person %, not to the gift idea''s recipient %',
            new.occasion_id, occasion_person_id, new.recipient_person_id
            using errcode = 'check_violation';
    end if;
    return new;
end;
$$;

create trigger check_occasion before insert or update of occasion_id, recipient_person_id on public.gift_ideas
    for each row execute function private.check_gift_idea_occasion();

create function private.check_gift_occasion()
    returns trigger
    language plpgsql
    set search_path = ''
as $$
declare
    occasion_person_id uuid;
begin
    if new.occasion_id is null then
        return new;
    end if;
    select o.person_id into occasion_person_id from public.occasions o where o.id = new.occasion_id;
    if occasion_person_id is not null
        and occasion_person_id is distinct from new.giver_person_id
        and occasion_person_id is distinct from new.recipient_person_id then
        raise exception 'occasion % belongs to person %, who neither gave nor received gift %',
            new.occasion_id, occasion_person_id, new.id
            using errcode = 'check_violation';
    end if;
    return new;
end;
$$;

create trigger check_occasion
    before insert or update of occasion_id, giver_person_id, recipient_person_id on public.gifts
    for each row execute function private.check_gift_occasion();
