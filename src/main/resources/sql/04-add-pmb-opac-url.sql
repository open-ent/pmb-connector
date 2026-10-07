-- URL de l'OPAC, l'interface PUBLIQUE de PMB (opac_css/), distincte de pmb_host qui pointe la
-- racine de l'installation — dont index.php est le BACK-OFFICE, réservé aux gestionnaires du
-- CDI. Les liens posés sur les notices remontées au médiacentre (consultation, et désormais
-- réservation d'un exemplaire) doivent viser l'OPAC : construits sur pmb_host, ils envoyaient
-- l'élève sur l'authentification bibliothécaire de PMB.
--
-- Facultative : laissée à NULL, le connecteur retombe sur `<pmb_host>/opac_css`, qui est la
-- disposition d'une installation PMB standard. À renseigner uniquement quand l'OPAC est exposé
-- ailleurs (autre domaine, réécriture d'URL devant PMB).
ALTER TABLE pmb.etablissement
  ADD COLUMN IF NOT EXISTS pmb_opac_url VARCHAR;
