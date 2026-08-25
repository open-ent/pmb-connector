-- Une même installation du connecteur peut désormais servir plusieurs établissements,
-- chacun avec son PROPRE serveur PMB (catalogue CDI propre à l'établissement, pas un
-- catalogue régional partagé). Les identifiants de connexion, jusqu'ici globaux
-- (ent-core.yaml -> PMB: {...}), sont donc rattachés à la ligne pmb.etablissement.
-- Un établissement secondaire d'une "cité scolaire" (id_principal non nul) laisse ces
-- colonnes NULL et hérite de la connexion de son établissement principal (cf.
-- PmbController.principalOrDefaultUAI, déjà utilisé pour la même notion de regroupement).
ALTER TABLE pmb.etablissement
  ADD COLUMN IF NOT EXISTS pmb_host VARCHAR,
  ADD COLUMN IF NOT EXISTS pmb_endpoint VARCHAR,
  ADD COLUMN IF NOT EXISTS pmb_source_id VARCHAR,
  ADD COLUMN IF NOT EXISTS pmb_username VARCHAR,
  ADD COLUMN IF NOT EXISTS pmb_password VARCHAR,
  ADD COLUMN IF NOT EXISTS pmb_page_size INTEGER;
