-- A.N.O.T.E. - Esquema do banco local (SQLite)
-- Mesmo esquema criado em NotesDbHelper.onCreate() (versao 2 do banco).

-- DDL
CREATE TABLE IF NOT EXISTS notes (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    title      TEXT    NOT NULL,
    content    TEXT    NOT NULL,
    updated_at INTEGER NOT NULL,            -- epoch em milissegundos
    pinned     INTEGER NOT NULL DEFAULT 0   -- 1 = nota fixada (secao "FIXADAS")
);

-- Migracao da versao 1 para a 2 (feita em NotesDbHelper.onUpgrade)
-- ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0;

-- DML de exemplo (usado em testes manuais)
INSERT INTO notes (title, content, updated_at, pinned) VALUES
    ('Lista de Compras', 'Arroz, feijão, leite, ovos, frutas da estação.', strftime('%s','now') * 1000, 1),
    ('Reunião de terça', 'Pauta: relatório trimestral, alinhamento com equipe.', strftime('%s','now') * 1000, 0),
    ('Receitas favoritas', 'Bolo de cenoura com cobertura de chocolate.', strftime('%s','now') * 1000, 0);

-- Listagem: fixadas primeiro, depois as mais recentes
SELECT id, title, content, updated_at, pinned FROM notes ORDER BY pinned DESC, updated_at DESC;

-- Busca por texto
SELECT id, title FROM notes WHERE lower(title) LIKE '%compras%' OR lower(content) LIKE '%compras%';

UPDATE notes SET content = 'Pauta: relatório e metas.', updated_at = strftime('%s','now') * 1000 WHERE id = 2;
UPDATE notes SET pinned = 1 WHERE id = 3;
DELETE FROM notes WHERE id = 3;

-- Observacao: o titulo-gatilho, os contatos de confianca e o estado de login NAO ficam
-- neste banco. Ficam em EncryptedSharedPreferences (criptografia AES-256), para nao
-- aparecerem em uma inspecao do arquivo notes.db.
