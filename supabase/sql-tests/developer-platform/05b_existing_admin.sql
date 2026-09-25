-- マイグレーション適用「前」から存在する、運営(管理者)の開発者と、その公開済みアプリ
insert into auth.users (id, email) values ('e0000000-0000-0000-0000-00000000000e', 'admin@example.com');
update public.profiles set role = 'admin' where id = 'e0000000-0000-0000-0000-00000000000e';
insert into public.developers (user_id, name, contact_email, status)
values ('e0000000-0000-0000-0000-00000000000e', 'T-tech', 'admin@example.com', 'approved');
insert into public.apps (id, slug, package_name, developer_id, name, status, short_desc, description)
values ('aaaaaaaa-0000-0000-0000-000000000001', 'admin-app', 'com.example.admin', 'e0000000-0000-0000-0000-00000000000e', '管理アプリ', 'published', '説明', 'くわしい説明');
