-- Create Player Table
CREATE TABLE IF NOT EXISTS player (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    balance DECIMAL(20, 2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0)
);

-- Create Transaction Table
CREATE TABLE IF NOT EXISTS wallet_transaction (
    transaction_id UUID PRIMARY KEY,
    player_id UUID NOT NULL,
    amount DECIMAL(20, 2) NOT NULL CHECK (amount > 0),
    type VARCHAR(20) NOT NULL CHECK (type IN ('DEBIT', 'CREDIT')),
    balance_after DECIMAL(20, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_player FOREIGN KEY (player_id) REFERENCES player(id) ON DELETE RESTRICT
);

-- Create Index for faster lookup of player history (Composite Index)
CREATE INDEX IF NOT EXISTS idx_transaction_player_time 
ON wallet_transaction(player_id, created_at DESC);