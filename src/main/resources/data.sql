-- Insert a test player (ID: 123e4567-e89b-12d3-a456-426614174000)
-- Default Balance: 100.00
INSERT INTO player (id, name, balance) 
VALUES ('123e4567-e89b-12d3-a456-426614174000', 'Test Player', 100.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO player (id, name, balance) 
VALUES ('e0e0e0e0-e0e0-e0e0-e0e0-e0e0e0e0e0e0', 'Docs Player', 100.00)
ON CONFLICT (id) DO NOTHING;