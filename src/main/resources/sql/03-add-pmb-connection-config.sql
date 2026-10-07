-- Une même installation du connecteur peut désormais servir plusieurs établissements,
-- chacun avec son PROPRE serveur PMB (catalogue CDI propre à l'établissement, pas
-- nécessairement un catalogue régional partagé). Les identifiants de connexion,
-- jusqu'ici globaux (ent-core.yaml -> PMB: {...}), sont donc rattachés à la ligne
-- pmb.etablissement.
--
-- id_principal (déjà utilisé pour les "cités scolaires" partageant physiquement un même
-- CDI) est en fait un mécanisme générique de PARTAGE DE CONNEXION, pas spécifique aux
-- cités scolaires : un établissement qui n'a pas son propre PMB peut tout aussi bien
-- pointer id_principal vers une ligne "virtuelle" (pas un établissement réel, juste un
-- catalogue régional/départemental mutualisé) portant les pmb_* d'une base commune. Dans
-- les deux cas, l'établissement secondaire laisse ses colonnes pmb_* à NULL et hérite de
-- la connexion résolue via PmbController.principalOrDefaultUAI.
ALTER TABLE pmb.etablissement
  ADD COLUMN IF NOT EXISTS pmb_host VARCHAR,
  ADD COLUMN IF NOT EXISTS pmb_endpoint VARCHAR,
  ADD COLUMN IF NOT EXISTS pmb_source_id VARCHAR,
  ADD COLUMN IF NOT EXISTS pmb_username VARCHAR,
  ADD COLUMN IF NOT EXISTS pmb_password VARCHAR,
  ADD COLUMN IF NOT EXISTS pmb_page_size INTEGER;
