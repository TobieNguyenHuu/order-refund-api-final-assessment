-- Mat khau: Admin@12345 / User@12345 (BCrypt, strength 10)
INSERT INTO users (username, email, password, enabled) VALUES
    ('adminuser', 'admin@example.com',
     '$2b$10$ldc.K/R6taVx6vy3PaVjt.d9lKTreCmfRS.te/7dn8qRxBsMEodKy', TRUE),
    ('useralpha', 'alpha@example.com',
     '$2b$10$KIyrYVhVh5mz0vlblKW2gudruPArJ9seHTFVbkHo8wQtce6FtgiDa', TRUE),
    ('userbeta',  'beta@example.com',
     '$2b$10$KIyrYVhVh5mz0vlblKW2gudruPArJ9seHTFVbkHo8wQtce6FtgiDa', TRUE);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE (u.username = 'adminuser' AND r.role_name = 'ADMIN')
   OR (u.username IN ('useralpha','userbeta') AND r.role_name = 'USER');
