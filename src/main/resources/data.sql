-- Seed default admin user (password: admin123, BCrypt encoded)
MERGE INTO app_user (id, username, password, role)
KEY (username)
VALUES (1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN');
