INSERT INTO category (name, parent_id) VALUES
    ('Еда', NULL),
    ('Транспорт', NULL);
INSERT INTO category (name, parent_id) VALUES
    ('Кафе', (SELECT id FROM category WHERE name = 'Еда')),
    ('Такси', (SELECT id FROM category WHERE name = 'Транспорт'));