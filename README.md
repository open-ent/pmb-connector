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

<pre>
{
  "config": {
    ...
    "infraMail": "${infraMailPmb}",
    "PMB": {
        "host": "${pmbServer}",
        "endpoint": "${pmbEndpoint}",
        "source_id": "${pmbSourceId}",
        "page_size": ${pmbPageSize},
        "credentials": {
            "username": "${pmbUsername}",
            "password": "${pmbPassword}"
        }
    }
  }
}
</pre>

Dans votre springboard, vous devez inclure des variables d'environnement :
<pre>
infraMailPmb = ${String}
pmbServer = ${String}
pmbEndpoint = Integer
pmbSourceId = ${String}
pmbPageSize = Integer
pmbUsername = ${String}
pmbPassword = ${String}
</pre>

`pmbSourceId` est l'identifiant de la source de connecteur sortant **apijsonrpc** créée côté
admin PMB (Administration > Connecteurs > Sortants > ajouter une source), PAS un préfixe/nom de
base de données — `endpoint` (`ws/connector_out.php`) n'accepte aucun paramètre `database`. Le
webservice doit en outre être autorisé pour un groupe d'utilisateurs externes (Administration >
Utilisateurs externes) auquel `pmbUsername`/`pmbPassword` doit correspondre (authentification
Basic ou identifiants d'un utilisateur externe PMB, comparés en clair côté PMB).
