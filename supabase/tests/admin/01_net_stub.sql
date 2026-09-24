-- pg_net(net.http_post)の代用品。呼ばれた内容を net.calls に記録するだけ(検証用の使い捨てDB専用)
create schema if not exists extensions;
create schema net;
create table net.calls (id bigserial primary key, url text, body jsonb, headers jsonb);
create table net.behavior (fail boolean not null default false);
insert into net.behavior values (false);

create function net.http_post(url text, body jsonb default '{}'::jsonb, params jsonb default '{}'::jsonb,
  headers jsonb default '{"Content-Type": "application/json"}'::jsonb, timeout_milliseconds integer default 5000)
returns bigint language plpgsql as $$
begin
  if (select fail from net.behavior) then raise exception 'net down'; end if;
  insert into net.calls (url, body, headers) values (url, body, headers);
  return 1;
end $$;
