# À propos de l'application PMB

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright CGI, Région Nouvelle Aquitaine
* Financeur(s) : CGI, Nouvelle Aquitaine
* Développeur(s) : CGI
* Description : module permettant la connexion à PMB.

# Présentation du module 

PMB est un outil qui regroupe les références des documents disponibles au CDI.
Il donne des renseignements pour accéder aux supports réels (livre, magazine, DVD, CD…)
et des liens pour accéder aux supports virtuels (sites internet et livres numériques).
Il permet également la réservation des ressources du CDI ainsi que leur suivi.

## Configuration

Chaque établissement a son **propre serveur PMB** (catalogue CDI propre à l'établissement,
pas un catalogue régional partagé) : `host`/`endpoint`/`source_id`/`credentials` ne sont donc
**plus des variables d'environnement globales**, mais des colonnes de la table
`pmb.etablissement` (`pmb_host`, `pmb_endpoint`, `pmb_source_id`, `pmb_username`,
`pmb_password`, `pmb_page_size`), gérées via l'API `SuperAdminFilter` ci-dessous. `PMBServer`
n'est plus une instance globale unique mais un registre par UAI (`PMBServer.get(uai)`),
réenregistré à chaque `amass()` à partir de ces colonnes (`PMBServer.register`, cf.
`PmbController.amass` / `AmassWorker`). Un établissement pas encore configuré est simplement
ignoré (log + rapport d'amass), il ne fait pas échouer les autres.

<pre>
{
  "config": {
    ...
    "infraMail": "${infraMailPmb}",
    "PMB": {
        "page_size": ${pmbPageSize}
    }
  }
}
</pre>

`page_size` reste un défaut global (nombre de notices par page lors de l'amass),
surchageable par établissement via `pmb_page_size`.

### Configurer la connexion PMB d'un établissement

1. Créer (ou avoir déjà) la ligne `pmb.etablissement` de l'établissement — `POST /pmb/schools`
   (`idneo`, `uai`, `nom`, `principal`, `id_principal`). `id_principal` sert à PARTAGER une
   connexion déjà configurée : cas des "cités scolaires" (plusieurs UAI, un même CDI
   physique), mais aussi d'un établissement sans PMB propre qui utilise un catalogue
   régional/départemental mutualisé — dans les deux cas l'UAI secondaire laisse `pmb_*` vide
   et hérite de la connexion de la ligne visée par `id_principal` (établissement réel ou
   simple ligne "virtuelle" ne représentant que la connexion partagée).
2. Renseigner sa connexion PMB — `PUT /pmb/schools/:schoolId/connection` :
<pre>
{
  "pmbHost": "https://pmb.etablissement.fr",
  "pmbEndpoint": "/pmb/ws/connector_out.php",
  "pmbSourceId": "1",
  "pmbUsername": "entconnector",
  "pmbPassword": "..."
}
</pre>

L'étape 1 (`POST /pmb/schools`) est réservée au super-administrateur (`SuperAdminFilter`) —
opération structurelle (idneo arbitraire, orchestration d'une cité scolaire). L'étape 2
(`PUT /pmb/schools/:schoolId/connection`) accepte en plus un **administrateur local**
(`AdminFilter`), mais uniquement pour SON PROPRE établissement : `SchoolController` vérifie que
l'`idneo` de l'établissement visé figure dans `user.getStructures()`, sinon 403. Il existe aussi
`GET /pmb/schools/mine` (même règle) pour lister l'établissement courant sans exposer les autres.

Un écran dédié dans le dashboard (`/admin/configuration/pmb`) s'appuie sur ces routes — voir la
documentation du connecteur PMB dans `open-ent/docs`.

`pmbSourceId` est l'identifiant de la source de connecteur sortant **apijsonrpc** créée côté
admin PMB (Administration > Connecteurs > Sortants > ajouter une source), PAS un préfixe/nom de
base de données — `pmbEndpoint` (`ws/connector_out.php`) n'accepte aucun paramètre `database`. Le
webservice doit en outre être autorisé pour un groupe d'utilisateurs externes (Administration >
Utilisateurs externes) auquel `pmbUsername`/`pmbPassword` doit correspondre (authentification
Basic ou identifiants d'un utilisateur externe PMB, comparés en clair côté PMB).
